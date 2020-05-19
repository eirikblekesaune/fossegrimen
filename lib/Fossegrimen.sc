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
	var masterVolume;
	var <soundFileStrategy = \fromdisk;
	var <oscResponders;
	var <fosselydStarter;
	var <fosselydStopper;
	var presenceModeActivationOverride = false;
	var presenceModeDeactivator;
	var presenceModeReactivator;
	var autoTurnoffMusikklydProcess;
	var willReactivatePresenceMode = false;

	*new{arg projectRootFolder, doWhenInitialized;
		^super.new.init(projectRootFolder, doWhenInitialized);
	}

	init{|projectRootFolder_, doWhenInitialized|
		var startPlayAfterInit = false;
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
		this.prInitTimevars;

		if(config.includesKey("soundFileStrategy"), {
			soundFileStrategy = config["soundFileStrategy"].asSymbol;
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

		fork{
			var cond = Condition.new;
			server = this.class.startServer(this, cond, config["serverOptions"]);
			if(server.isNil, {
				Error("[Fossegrimen] Server failed to start").throw;
			});
			outbus = Bus.audio(server, 2);
			server.sync;
			this.masterVolume_(0);
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

	prInitTimevars{
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
			h: (val: 180, spec: timevarSpec.value),

			// time between new file start after <prescence mode> 
			//was activated.
			i: (val: 2, spec: timevarSpec.value),

			//pause between next file in <musikklyd> players playing
			j: (val: 0, spec: timevarSpec.value),

			//duration of overrideDeactivation
			k: (val: 20, spec: timevarSpec.value),
		);
		//load presets
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
		this.currentTimevarPreset_(\default);
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
			if(presenceModeDeactivator.notNil, {
				presenceModeDeactivator.stop;
			});
			if(presenceModeReactivator.notNil, {
				presenceModeReactivator.stop;
			});
			players.do({|player| player.play_(false)});
			this.changed(\isPlaying);
			"Stopped playing".postln;
		}, {
			"Already stopped".postln;
		});
	}


	mode_{|val, forceMode = false|
		if([\absence, \presence].includes(val), {
			if(mode != val or: {forceMode}, {
				mode = val;
				if(fosselydStarter.notNil, {
					fosselydStarter.stop;
				});
				if(fosselydStopper.notNil, {
					fosselydStopper.stop;
				});
				if(presenceModeDeactivator.notNil, {
					presenceModeDeactivator.stop;
				});
				if(presenceModeReactivator.notNil, {
					presenceModeReactivator.stop;
				});
				switch(mode,
				\absence, {
					"Absence mode started".postln;
					if(willReactivatePresenceMode, {
						var reactivationSecs = this.getTimevarValue(\k);
						"Will reactivate presence mode in % (k) seconds".format(
							reactivationSecs
						).postln;
						if(presenceModeReactivator.notNil, {
							presenceModeReactivator.stop;
						});
						presenceModeReactivator = fork{
							reactivationSecs.wait;
							fork{
								"Reactivated presence mode".postln;
								this.mode_(\presence);
							}
						};
					});
					fosselydStarter = fork{
						this.getTimevarValue(\e).wait;
						players['fosselyd'].play_(true, (
							fadeInTime: this.getTimevarValue(\f)
						));
					};
				},
				\presence, {
					willReactivatePresenceMode = false;
					fosselydStopper = fork{
						this.getTimevarValue(\c).wait;
						players['fosselyd'].play_(false, (
							fadeOutTime: this.getTimevarValue(\d)
						))
					};
					presenceModeDeactivator = fork{
						var presenceModeDeactivateSecs;
						presenceModeDeactivateSecs = this.getTimevarValue(\h);
						"Waiting % secs (h) for auto deactivate presence mode".format(
							presenceModeDeactivateSecs
						).postln;
						presenceModeDeactivateSecs.wait;
						fork{
							"Auto-Deactivated presence mode. Will reactivate.".postln;
							willReactivatePresenceMode = true;
							this.mode_(\absence);
						}
					};
					if(autoTurnoffMusikklydProcess.notNil, {
						autoTurnoffMusikklydProcess.stop;
					});
					autoTurnoffMusikklydProcess = fork{
						var autoTurnoffMusikklydSecs;
						autoTurnoffMusikklydSecs = this.getTimevarValue(\g);
						"Waiting % (g) seconds for musikklyd auto turnoff".format(
							autoTurnoffMusikklydSecs
						).postln;
						autoTurnoffMusikklydSecs.wait;
						"Auto-Turning off musikklyd now".postln;
						players[\musikklyd].play_(false);
					};
					"Presence mode started".postln;
					players['musikklyd'].play_(true);
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

	presenceSensor_{|aBool|
		if(presenceSensor != aBool, {
			if(aBool, {
				presenceSensor = true;
				"Someone arrived".postln;
			}, {
				presenceSensor = false;
				"Someone left".postln;
			});
			this.changed(\presenceSensor);
			if(sensorMuted.not and: {isPlaying}, {
				var newMode;
				if(presenceSensor, {
					newMode = \presence;
				}, {
					newMode = \absence;
				});
				this.mode_(newMode);
			});
		});
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
				nil
			)
		);
		window.onClose_({this.stop;});

		^window;
	}

	stop{
		"Stopping Fossegrimen".postln;
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
}
