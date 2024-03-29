FossegrimenRuntime{
	var <projectRootPathName;
	var <config;
	var <server;
	var <outbus;
	var <players;
	var <timevars;
	var <timevarPresets;
	var <currentTimevarPreset;
	var <isPlaying = false;
	var <mode = \absence;
	var <presenceSensor = false;
	var <sensorMuted = false;
	var runLoop;
	var <soundFileStrategy = \fromdisk;
	var <oscResponders;
	var presenceModeMusikklydProcess;
	var presenceModeFosselydProcess;
	var absenceProcess;
	var <hangConditions;
	var <sensorThreshold;
	var sensorHardwareAddress;
	var noMusikklydChannelsPlaying;
	var <threadPool;
	var timevarKWait;
	var <musikklydVolume;
	var <fosselydVolume;

	*new{arg projectRootFolder, doWhenInitialized;
		^super.new.init(projectRootFolder, doWhenInitialized);
	}

	init{|projectRootFolder_, doWhenInitialized|
		var startPlayAfterInit = false;
		var startPreset;
		projectRootPathName = PathName(projectRootFolder_);
		"Fossegrimen running from path: %".format(
			projectRootPathName.fullPath
		).postln;
		"sclang running from %".format("pwd".unixCmdGetStdOut).postln;
		"Data path: %".format(this.dataPathName.fullPath).postln;
		if(File.exists(this.configPathName.fullPath), {
			config = this.configPathName.fullPath.parseYAMLFile;
		}, {
			"Config file '%' not found. Using default settings.".format(
				this.configPathName.fullPath
			).postln;
			config = Dictionary[
				"fosselyderFolder" -> "sounds/fosselyder".resolveRelative,
				"musikklyderFolder" -> "sounds/musikklyder".resolveRelative
			];
		});
		if(config.includesKey("startPreset"), {
			startPreset = config["startPreset"].asSymbol;
		});
		this.prInitTimevars(startPreset ? \default);
		noMusikklydChannelsPlaying = Condition.new(true);
		timevarKWait = Condition.new;
		hangConditions = IdentitySet.new;
		threadPool = List.new;

		if(config.includesKey("soundFileStrategy"), {
			soundFileStrategy = config["soundFileStrategy"].asSymbol;
		});

		if(config.includesKey("sensorThreshold"), {
			sensorThreshold = config["sensorThreshold"].asFloat;
		}, {
			sensorThreshold = 0.5;
		});

		if(config.includesKey("sensorHardwareAddress"), {
			var ip, port;
			#ip, port = config["sensorHardwareAddress"].split($:);
			sensorHardwareAddress = NetAddr(
				ip, port.asInteger
			);
			//We presume that the hardware is running now, so
			//sending the sensorThreshold data to it.
			this.prSendSensorThresholdToHardware;
		});
		this.prInitSoundFilesFolders;
		if(config.includesKey("startPlayAfterInit"), {
			var str = config["startPlayAfterInit"];
			if(str == "true", {
				startPlayAfterInit = true;
			}, {
				startPlayAfterInit = false;
			});
		});
		if(config.includesKey("musikklydVolume"), {
			musikklydVolume = config["musikklydVolume"].asFloat;
		}, {
			musikklydVolume = 0.0;
		});
		if(config.includesKey("fosselydVolume"), {
			fosselydVolume = config["fosselydVolume"].asFloat;
		}, {
			fosselydVolume = 0.0;
		});

		fork{
			var cond = Condition.new;
			var masterVolume;
			if(config.includesKey("masterVolume"), {
				masterVolume = config["masterVolume"].asFloat;
			}, {
				masterVolume = 0.0;
			});
			server = this.class.startServer(this, cond, config["serverOptions"]);
			if(server.isNil, {
				Error("[Fossegrimen] Server failed to start").throw;
			});
			outbus = Bus.audio(server, 2);
			server.sync;
			this.masterVolume_(masterVolume);
			{server.makeWindow;}.defer;

			this.prInitPlayers(cond);

			this.startOSC;
			this.changed(\initialized);
			doWhenInitialized.value(this);
			if(startPlayAfterInit, {
				this.play_(true);
			})
		};
	}

	prInitTimevars{|startPreset|
	 	var timevarSpec = {ControlSpec(0.01, 360.0, \amp, units: \secs)};
		timevarPresets = Dictionary.new;
		timevars = (
			//overlap between <fosselyd> players
			a: (val: 10, spec: timevarSpec.value),

			//fade in time for <fosselyd> player
			b: (val: 10, spec: timevarSpec.value),

			//start fade out <fosselyd> after <prescence mode>
			//activated
			c: (val: 12, spec: timevarSpec.value),

			//fade out time for <fosselyd> after <prescence mode>
			//activated
			d: (val: 15, spec: timevarSpec.value),

			// wait time after <absence mode> activated, before 
			// playing <fosselyd>
			e: (val: 2, spec: timevarSpec.value),

			// fade in time for <fosselyd>
			f: (val: 26, spec: timevarSpec.value),

			// time delay after <abscence mode> started for to
			// freeze the presence
			// sounds and play them to the end.
			g: (val: 46, spec: timevarSpec.value),

			// max <prescence mode> duration, time before 
			//overrideDeactivation
			h: (val: 60, spec: timevarSpec.value),

			// time between new file start after <prescence mode> 
			//was activated.
			i: (val: 2, spec: timevarSpec.value),

			//pause between next file in <musikklyd> players playing
			j: (val: 0, spec: timevarSpec.value),

			//duration of overrideDeactivation
			k: (val: 20, spec: timevarSpec.value),
		);
		//load presets
		this.prLoadPresetsFromPresetFolder;
		timevarPresets.put(\default, (
			a: timevars[\a].val,
			b: timevars[\b].val,
			c: timevars[\c].val,
			d: timevars[\d].val,
			e: timevars[\e].val,
			f: timevars[\f].val,
			g: timevars[\g].val,
			h: timevars[\h].val,
			i: timevars[\i].val,
			j: timevars[\j].val,
			k: timevars[\k].val
		));
		this.currentTimevarPreset_(startPreset ? \default);
	}

	prLoadPresetsFromPresetFolder{
		this.presetPathName.files.select({|p|
			p.fileName.endsWith(".fossepreset")
		}).do({|p|
			var text = File.readAllString(p.fullPath);
			var presetName = p.fileNameWithoutExtension.asSymbol;
			var timevarLetters = timevars.keys.asArray.join;
			timevarPresets.put(presetName, ());
			text.split(Char.nl).select({|line|
				"^[%]=.+$".format(timevarLetters).matchRegexp(line);
			}).do({|line|
				var letter, value;
				#letter, value = line.split($=);
				timevarPresets[presetName].put(letter.asSymbol, value.asFloat);
			});
		});
		this.changed(\presetNames);
	}

	presetNames{
		^timevarPresets.keys.asArray.sort;
	}

	currentTimevarPreset_{|presetName|
		if(timevarPresets.includesKey(presetName), {
			"setting preset: %".format(presetName).postln;
			currentTimevarPreset = presetName;
			this.changed(\currentTimevarPreset);
			timevarPresets[currentTimevarPreset].keysValuesDo({|k,v|
				this.setTimevarValue(k,v);
			});
		})
	}

	setTimevarValue{|letter, val|
		var timevar = timevars[letter];
		timevar[\val] = timevar[\spec].constrain(val);
		timevar.changed(\val);
		"Timevar '%' val: %".format(letter, val).postln;
	}

	getTimevarValue{|letter|
		^timevars[letter][\val];
	}

	storeCurrentTimevarsToPreset{|presetName|
		Routine({
			var str;
			var presetFilePath;
			var file;
			var willStore = false;
			timevars.keys.asArray.sort.do({|key|
				var val, timevar;
				timevar = timevars[key];
				str = str.add(
					"%=%\n".format(key, timevar[\val]);
				);
			});
			str = str.join;
			presetFilePath = "%/%.fossepreset".format(
				this.presetPathName.fullPath,
				presetName
			);
			if(File.exists(presetFilePath), {
				var cond = Condition.new;
				var overwrite = false;
				var win = Window();
				win.layout_(
					VLayout(
						StaticText().string_("Presetfile already exists, Overwrite?"),
						HLayout(
							Button()
							.states_([["OK"]])
							.action_({
								overwrite=true;
								cond.unhang;
							}),
							Button()
							.states_([["CANCEL"]])
							.action_({
								overwrite=false;
								cond.unhang;
							})
						)
					)
				).front;
				cond.hang;
				win.close;
				if(overwrite, {
					willStore = true;
				});
			}, {
				willStore = true;
			});
			if(willStore, {
				file = File.open(presetFilePath, "w");
				if(file.isOpen, {
					file.write(str);
					file.close();
					"Wrote current timevars to preset file: '%'".format(
						presetFilePath
					).postln;
					this.prLoadPresetsFromPresetFolder;
					this.currentTimevarPreset_(presetName.asSymbol);
				}, {
					"Failed making preset file:'%'".format(
						presetFilePath
					).warn;
				});
			});
		}).play(AppClock);
	}

	prInitPlayers{| cond |
		var fosselydPlayer, musikklydPlayer;
		players = IdentityDictionary.new;
		cond.test = false;
		fosselydPlayer = FosselydPlayer(
			this,
			config["fosselyderFolder"].standardizePath,
			{
				"Fosslyder player was initalized".postln;
			},
			doWhenInitialized: {|p|
				cond.test = true;
				cond.signal;
			}
		);
		cond.wait;
		cond.test = false;
		fosselydPlayer.volume_(fosselydVolume);
		musikklydPlayer = MusikklydPlayer(
			this,
			config["musikklyderFolder"].standardizePath,
			{
				"Musikklyder player was initalized".postln;
			},
			doWhenInitialized: {|p|
				cond.test = true;
				cond.signal;
			}
		);
		cond.wait;
		musikklydPlayer.volume_(musikklydVolume);
		players['fosselyd'] = fosselydPlayer;
		players['musikklyd'] = musikklydPlayer;

	}

	prInitSoundFilesFolders{
		if(config.includesKey("fosselyderFolder").not, {
			config.put("fosselyderFolder", "sounds/fosselyder".resolveRelative);
		});
		if(config.includesKey("musikklyderFolder").not, {
			config.put("musikklyderFolder", "sounds/musikklyder".resolveRelative);
		});
	}

	//Must be called within a Routine and with a Condition instance as arg
	*startServer{|runtime, cond, serverOptions|
		var server, maxBootTimeChecker;
		var serverWasBooted = false;
		var o = ServerOptions.new;
		server = Server.default;
		if(serverOptions.notNil, {
			if(serverOptions.includesKey("protocol"), {
				o.protocol_(serverOptions["protocol"].asSymbol);
			});
		});
		server.options = o;
		server.waitForBoot({
			serverWasBooted = true;
			cond.test = true;
			cond.signal;
		});
		maxBootTimeChecker = fork{
			10.wait; //give up after 10 seconds
			cond.test = true;
			cond.signal;
		};
		cond.wait;
		if(serverWasBooted, {
			//load SynthDefs
			[1,2].do({|numChannels|
				SynthDef("diskPlayer%".format(numChannels).asSymbol, {
					|
					out= 0,amp = 0.1, bufnum, gate=1,
					attack = 0.01, release= 0.01, rate = 1, duration = 1.0
					|
					var sig, env, gateEnv;
					var elapsedSeconds;
					gateEnv = EnvGen.kr(Env([1,1,0], [duration - release, 0]));
					env = EnvGen.kr(
						Env(
							[0.001, 1.0, 1.0, 0.001],
							[attack, 1.0, release],
							[2.0,0.0,-2.0],
							releaseNode: 1
						),
						gate * gateEnv,
						doneAction: 2
					);
					elapsedSeconds = Sweep.ar(Impulse.ar(0), 1.0);
					SendReply.kr(Impulse.kr(4), '/elapsedSeconds', elapsedSeconds);
					sig = VDiskIn.ar(
						numChannels, bufnum, rate: BufRateScale.kr(bufnum) * rate
					);
					Out.ar(out, sig * env * amp);
				}).add;
				server.sync;
				SynthDef("bufferPlayer%".format(numChannels).asSymbol, {
					|
					out= 0,amp = 0.1, bufnum, gate=1,
					attack = 0.01, release= 0.01, rate = 1, duration = 1.0
					|
					var sig, env, gateEnv;
					var elapsedSeconds;
					gateEnv = EnvGen.kr(Env([1,1,0], [duration - release, 0]));
					env = EnvGen.kr(
						Env(
							[0.001, 1.0, 1.0, 0.001],
							[attack, 1.0, release],
							[2.0,0.0,-2.0],
							releaseNode: 1
						),
						gate * gateEnv,
						doneAction: 2
					);
					elapsedSeconds = Sweep.ar(Impulse.ar(0), 1.0);
					SendReply.kr(Impulse.kr(4), '/elapsedSeconds', elapsedSeconds);
					sig = PlayBuf.ar(
						numChannels, bufnum, rate: BufRateScale.kr(bufnum) * rate
					);
					Out.ar(out, sig * env * amp);
				}).add;
				server.sync;
			});
			^server;
		});
		^nil; //return nil if server didnt boot
	}

	dataPathName{
		^projectRootPathName +/+ "data";
	}

	configPathName{
		^this.dataPathName +/+ "Fossegrimen.conf.yaml"
	}

	presetPathName{
		^this.dataPathName +/+ "presets";
	}

	play_{|aBool|
		if(aBool, {
			this.prStartPlaying;
		}, {
			this.prStopPlaying
		})
	}

	prStartPlaying{
		if(isPlaying.not, {
			var startupMode;
			if(sensorMuted, {
				startupMode = mode;
			}, {
				if(presenceSensor, {
					startupMode = \presence;
				}, {
					startupMode = \absence;
				})
			});
			isPlaying = true;
			this.mode_(startupMode, forceMode: true);
			this.changed(\isPlaying);
			"Started Playing".postln;
		}, {
			"Already playing".postln;
		});
	}

	prStopPlaying{
		if(this.isPlaying, {
			isPlaying = false;
			players.do({|player| player.play_(false)});
			this.prStopProcesses;
			this.changed(\isPlaying);
			"Stopped playing".postln;
		}, {
			"Already stopped".postln;
		});
	}

	prStopProcesses {
		if(presenceModeFosselydProcess.notNil, {
			this.prStopThread(\presenceModeFosselydProcess, presenceModeFosselydProcess);
		});
		if(presenceModeMusikklydProcess.notNil, {
			this.prStopThread(\presenceModeMusikklydProcess, presenceModeMusikklydProcess);
		});
		if(absenceProcess.notNil, {
			this.prStopThread(\absenceProcess, absenceProcess);
		});
	}

	prStartThread{|name, func|
		var thread = func.play;
		this.notify("Adding thread % to pool: %".format(thread.identityHash, thread));
		threadPool.add((name: name, thread: thread));
		this.changed(\threadStarted, thread, name);
	}

	prStopThread{|name, thread|
		var threadIndex = threadPool.detectIndex({|threadData|
			threadData[\thread] === thread and: {threadData[\name] == name}
		});
		if(threadIndex.notNil, {
			this.notify("Removing thread % from pool: %".format(thread.identityHash, name));
			thread.stop;
			threadPool.removeAt(threadIndex);
			this.changed(\threadStopped, thread, name);
		});
	}

	mode_{|val, forceMode = false|
		if([\absence, \presence].includes(val), {
			if(mode != val or: {forceMode}, {
				mode = val;
				switch(mode,
					\absence, {
						this.notify("Absence mode started");
						absenceProcess = Routine({
							var fosselydPreWait = this.getTimevarValue(\e);
							var fosselydFadeInTime;
							if(presenceModeFosselydProcess.notNil, {
								this.prStopThread(\presenceModeFosselydProcess, presenceModeFosselydProcess);
							});
							if(presenceModeMusikklydProcess.notNil, {
								this.prStopThread(\presenceModeMusikklydProcess, presenceModeMusikklydProcess);
							});
							players['musikklyd'].play_(false);
							this.notify("Waiting % seconds (e) before starting fosselyd in absence mode".format(
								fosselydPreWait
							));
							this.wait(fosselydPreWait, "Starting fosselyd");
							fosselydFadeInTime = this.getTimevarValue(\f);
							this.notify("Starting absence mode fosselyd with % seconds fade in time (f)".format(
								fosselydFadeInTime
							));
							players['fosselyd'].play_(true, (
								fadeInTime: fosselydFadeInTime
							));
						});
						this.prStartThread(\absenceProcess, absenceProcess);
					},
					\presence, {
						if(absenceProcess.notNil, {
							this.prStopThread(\absenceProcess, absenceProcess);
						});
						if(presenceModeFosselydProcess.notNil, {
							this.prStopThread(\presenceModeFosselydProcess, presenceModeFosselydProcess);
						});
						if(presenceModeMusikklydProcess.notNil, {
							this.prStopThread(\presenceModeMusikklydProcess, presenceModeMusikklydProcess);
						});
						this.notify("Presence mode started");
						presenceModeFosselydProcess = Routine({
							while({this.mode == \presence}, {
								var secondsBeforeFosselydStartsFadeout = this.getTimevarValue(\c);
								var fosselydFadeOutTime;
								var secondsBeforeRestartFosselyd;
								var secondsBeforeFosselydRevamp;
								var fosselydFadeInTime;
								this.notify("Will fade out fosselyd in % seconds (c)".format(
									secondsBeforeFosselydStartsFadeout
								));
								this.wait(secondsBeforeFosselydStartsFadeout, "Fade out fosselyd");
								//secondsBeforeFosselydStartsFadeout.wait;
								fosselydFadeOutTime = this.getTimevarValue(\d);
								this.notify("Starting fosselyd fadeout with fadeout time: % seconds (d)".format(
									fosselydFadeOutTime
								));
								players['fosselyd'].play_(false, (
									fadeOutTime: fosselydFadeOutTime
								));
								secondsBeforeRestartFosselyd = this.getTimevarValue(\h) + this.getTimevarValue(\e);
								this.notify("Will wait % seconds before restart fosselyd (h + e) - (c + d)".format(
									secondsBeforeRestartFosselyd - (secondsBeforeFosselydStartsFadeout - fosselydFadeOutTime)
								));
								this.wait(
									secondsBeforeRestartFosselyd - (secondsBeforeFosselydStartsFadeout - fosselydFadeOutTime),
									"Restart fosslyd in presence mode"
								);
								fosselydFadeInTime = this.getTimevarValue(\f);
								this.notify("Restarting fosselyd in presence mode with fade in time: %".format(
									fosselydFadeInTime
								));
								players['fosselyd'].play_(true, (
									fadeInTime: fosselydFadeInTime
								));
								this.notify("Will wait for all musikklyd channels to end before considering revamp fosselyd");
								this.hang(noMusikklydChannelsPlaying, "Fosslyd wait for musikklyd channels done playing");
								//secondsBeforeFosselydRevamp = this.getTimevarValue(\k);
								//this.notify("All musiklyd channels done. Will wait % seconds (k) before considering revamp".format(
								//	secondsBeforeFosselydRevamp
								//));
								//this.wait(secondsBeforeFosselydRevamp, "Fosselyd wait before considering revamp");
								this.hang(timevarKWait, "Wait for (k) wait in musikklyd to end");
								this.notify("Revamping fosselyd in presence mode if mode is still presence");
							});
						});
						presenceModeMusikklydProcess = Routine({
							var musikklydMaxDuration;
							while({this.mode == \presence}, {
								var secondsBeforeMusikklydRevamp;
								musikklydMaxDuration = this.getTimevarValue(\g) + this.getTimevarValue(\h);
								this.notify("Starting musikklyd with max duration % seconsd (g + h)".format(
									musikklydMaxDuration
								));
								players['musikklyd'].play_(true);
								noMusikklydChannelsPlaying.test = false;
								this.wait(musikklydMaxDuration, "Turning off musikklyd max duration");
								players['musikklyd'].play_(false, (
									onAllStopped: {
										this.notify("All channels stopped");
										noMusikklydChannelsPlaying.test = true;
										noMusikklydChannelsPlaying.signal;
									}
								));
								this.notify("Will wait for all musiklyd channels to end");
								timevarKWait.test = false;
								this.hang(noMusikklydChannelsPlaying, "Wait for musikklyd channels done playing");
								secondsBeforeMusikklydRevamp = this.getTimevarValue(\k);
								this.notify("Will wait % seconds before considering to revamp musikklyd".format(
									secondsBeforeMusikklydRevamp
								));
								this.wait(secondsBeforeMusikklydRevamp, "Starting musikklyd revamp");
								timevarKWait.test = true;
								timevarKWait.signal;
								this.prUpdateSensorForMode;
								this.notify("Revamping musikklyd now in presence mode");
							});
						});
						this.prStartThread(\presenceModeFosselydProcess, presenceModeFosselydProcess);
						this.prStartThread(\presenceModeMusikklydProcess, presenceModeMusikklydProcess);
						//					//wait g + h
						//					//si fra at dette blir siste runde til player
						//					//wait i k sekunder
						//					//hvis presence
						//					//  starte musikken igjen med ny bank
						//					//  akkurat som om noen gikk inn i sensoren igjen
						//					//
					});
					this.changed(\mode);
				})
			}, {
				"Unknown mode: '%'".format(val).postln;
			});
		}

		sensorMuted_{|aBool|
			"Muting sensor: %".format(aBool).postln;
			sensorMuted = aBool;
			this.changed(\sensorMuted);
		}

		isOpeningTime{
			^if (config.includesKey("openingTime") && config.includesKey("closingTime"), {
				var t = Date.localtime;
				var start = config["openingTime"].asSecs;
				var end = config["closingTime"].asSecs;
				var now = (3600*t.hour) + (60*t.minute);
				if (now > start and: { now < end } ) { true; } { false; };
			}, { true });
		}

		presenceSensor_{|aBool|
			// allow all sensors readings inside opening time, but always all sensor off
			var allowsensor = this.isOpeningTime() || (aBool == false);
			if((presenceSensor != aBool) && ( allowsensor ), {
				if(aBool, {
					presenceSensor = true;
					this.notify("Someone arrived");
				}, {
					presenceSensor = false;
					this.notify("Someone left");
				});
				this.changed(\presenceSensor);
				this.prUpdateSensorForMode;
			});
		}

		prUpdateSensorForMode{
			if(sensorMuted.not and: {isPlaying}, {
				//if the musikklyd is playing the sensor
				//won't control the mode until the last musikklyd
				//player channel has stopped
				if(noMusikklydChannelsPlaying.test, {
					if(presenceSensor, {
						this.mode_(\presence);
					}, {
						this.mode_(\absence);
					});
				});
			})
		}

		masterVolume{
			^server.volume.volume;
		}

		masterVolume_{|val|
			server.volume.volume_(val);
			this.changed(\masterVolume);
		}

		startOSC{
			oscResponders.addAll([
				OSCFunc({|msg, addr, time, port|
					var val;
					val = msg[1].booleanValue;
					this.presenceSensor_(val);
				}, '/fossegrimen/presenceSensor'),
				OSCFunc({|msg, addr, time, port|
					var val;
					val = msg[1].booleanValue;
					this.play_(val);
				}, '/fossegrimen/play'),
				OSCFunc({|msg, addr, time, port|
					var val;
					val = msg[1].asSymbol;
					this.mode_(val);
				}, '/fossegrimen/mode'),
				OSCFunc({|msg, addr, time, port|
					var val;
					val = msg[1].booleanValue;
					this.sensorMuted_(val);
				}, '/fossegrimen/muteSensor')
			]);
		}
		makeView{
			var unitSize = 20;
			var color = Color.yellow.alpha_(0.2);
			var font = Font("Avenir", 12);
			var window = Window("Fossegrimen", Rect(0,0,200,200));
			var meterViewParent = View();
			var meterView = ServerMeterView(
				server,
				meterViewParent, 0@0, 0, 2
			);
			var viewSettings = (
				unitSize: unitSize,
				color: color,
				font: font
			);
			window.view.layout_(
				VLayout(
					HLayout(
						VLayout(
							FossegrimenView.buildKnobView(
								label: "MASTER",
								unitSize: unitSize,
								font: font,
								color: color,
								spec: ControlSpec(-90, 6, \db, units: \dB),
								model: this,
								callback: {|val|
									this.masterVolume_(val);
								},
								listenTo: \masterVolume
							),
							meterViewParent
						),
						VLayout(
							FossegrimenView.buildTimevarPanel(this, viewSettings),
							FossegrimenView.buildPlayersPanel(this, viewSettings),
							FossegrimenView.buildButtonPanelView(this, viewSettings),
							nil
						).margins_(0).spacing_(0)
					).margins_(0).spacing_(0),
					HLayout(
						FossegrimenView.buildCountdownPanel(this, viewSettings),
						FossegrimenView.buildWaitConditionsPanel(this, viewSettings),
						FossegrimenView.buildThreadPoolPanel(this, viewSettings)
					),
					nil
				)
			);
			window.onClose_({this.stop;});

			^window;
		}

		sensorThreshold_{|val|
			sensorThreshold = val.clip(0.0,1.0);
			this.changed(\sensorThreshold);
			this.prSendSensorThresholdToHardware;
		}

		prSendSensorThresholdToHardware{
			if(sensorHardwareAddress.notNil, {
				sensorHardwareAddress.sendMsg('/sensorThreshold', sensorThreshold);
			})
		}

		stop{
			this.notify("Stopping Fossegrimen");
			fork{
				var cond = Condition.new;
				var serverQuitMaxWaitProcess;
				//Stop synth, free buffers, stop server etc.
				server.makeBundle(nil, {
					players.do({|player|
						player.free;
					});
					server.sync;
				});
				if(this.server.notNil, {
					var serverDidQuit = false;
					var server = this.server.quit(
						onComplete: {
							"Server quit OK".postln;
							serverDidQuit = true;
							cond.test = true;
							cond.signal;
						},
						onFailure:{
							"Server failed to quit".postln;
							serverDidQuit = false;
							cond.test = true;
							cond.signal;
						}
					);
					serverQuitMaxWaitProcess = fork{
						5.wait;
						cond.test = true;
						cond.signal;
					};
					cond.wait;
				});
				0.exit;
			};
		}

		wait{|val, description|
			var countdown;
			var parentThread = thisThread;
			//"Waiting for % seconds".format(val).postln;
			//"\t%".format(description).postln;
			countdown = fork{
				var remaining = val;
				var tickDuration = 1.0;
				loop{
					//"% in % seconds".format(description, remaining).postln;
					countdown.changed(\update, remaining);
					remaining = remaining - tickDuration;
					tickDuration.wait;
					if(parentThread.isPlaying.not, {
						countdown.changed(\countdownStopped, description);
						thisThread.stop;
					});
				}
			};
			this.changed(\countdownStarted, countdown, val, description);
			val.wait;
			countdown.stop;
			countdown.changed(\countdownStopped, description);
		}

		hang{|cond, description|
			var parentThread = thisThread;
			//"Waiting for condition".format(cond).postln;
			//"\t%".format(description).postln;
			if(hangConditions.includes(cond).not, {
				hangConditions.add(cond);
				this.changed(\waitConditionStarted, cond, description);
				cond.wait;
				hangConditions.remove(cond);
				cond.changed(\waitConditionEnded, description);
			}, {
				//If already registered we just wait here
				cond.changed(\additionalDependency, description);
				cond.wait;
			});
		}

		notify{|str|
			var timestr = Date.localtime.format("%H:%M:%S");
			"[%] - %".format(timestr, str).postln;
		}
	}

