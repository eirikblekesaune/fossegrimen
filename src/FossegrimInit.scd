(
	var args = currentEnvironment;
	var env = args[\env];
	env.use{
	 	var timevarSpec = {ControlSpec(0.01, 360.0, \amp, units: \secs)};
		var runtime;
		var timevars = (
			//overlap between <fosselyd> players
			a: (val: 10, spec: timevarSpec.value),
			//fade in time for <fosselyd> player
			b: (val: 10, spec: timevarSpec.value),
			//start fade out <fosselyd> after <prescence mode> activated
			c: (val: 12, spec: timevarSpec.value),
			//fade out time for <fosselyd> after <prescence mode> activated
			d: (val: 15, spec: timevarSpec.value),
			// wait time after <absence mode> activated, before playing <fosselyd>
			e: (val: 2, spec: timevarSpec.value),
			// fade in time for <fosselyd>
			f: (val: 26, spec: timevarSpec.value),
			// time delay after <abscence mode> started for to freeze the presence
			// sounds and play them to the end.
			g: (val: 46, spec: timevarSpec.value),
			// max <prescence mode> duration, time before overrideDeactivation
			h: (val: 180, spec: timevarSpec.value),
			// time between new file start after <prescence mode> was activated.
			i: (val: 2, spec: timevarSpec.value),
			//pause between next file in <musikklyd> players playing
			j: (val: 0, spec: timevarSpec.value),
			//duration of overrideDeactivation
			k: (val: 20, spec: timevarSpec.value),
		);
		var playerProto = (
			initialize: {|self|
				self.channels.do({|channel|
					channel.put(\player, self);
				});
			},
			volume: 0, // 0 - 100
			setVolume: {},
			soundFilesFolder: nil,
			soundFileDurations: Dictionary.new,
			soundFilePathNames: List.new,
			soundFileNamePattern: ".+", //accept all by default
			setSoundFileFolder: {|self, path|
				var playerType = self[\playerType];
				if(File.exists(path.standardizePath), {
					var pattern = self[\soundFileNamePattern];
					var pathName = PathName(path);
					self[\soundFilesFolder] = path;

					//Clear the previous stuff
					self[\soundFileDurations].clear();
					self[\soundFilePathNames].clear();

					//We're assuming that all sound files are in the same folder,
					//i.e. no recursive search.
					pathName.files.select({arg p;
						//Only find those that match the file pattern
						pattern.matchRegexp(p.fileName);
					}).do({arg p;
						var soundFile;
						//"Found '%' file: '%'".format(playerType, p).postln;
						self[\soundFilePathNames].add(p);
						soundFile = SoundFile.openRead(p.fullPath);
						self[\soundFileDurations].put(p.fullPath, soundFile.duration);
						soundFile.close;
					});
					"Loaded soundfiles for '%' from folder: '%'".format(
						playerType, pathName.fullPath
					).postln;
					self.changed(\loadedSoundFilesFolder);
				}, {
					"'%' folder not found at path: %".format(playerType, path).warn;
				});
			},
			getSoundFilesFolderSize: {|self|
				runtime.getFolderSizeInMB(self[\soundFilesFolder]);
			}
		);
		var playerChannelProto = (
			willLoop: false,
			playProcess: Routine{},
			startLoop: {|self|
				"Starting loop for % of role: %".format(
					self.name, self.role
				).postln;
				self[\willLoop] = true;
				self[\playProcess] = Routine{
					while({self[\willLoop]}, {
						var pathName = self.player.findSoundFilePathName;
						var duration = self.player.soundFileDurations[pathName.fullPath];
						"[%]play next musikkfil: '%'".format(self.name, pathName).postln;
						self.playSoundFile( pathName, duration );
						duration.wait;	
						//only wait here if we are not looping
						if(self[\willLoop], {
							0.1.wait; //timevar 'j'
						});
					});
					self.changed('stoppedPlaying');
					"[%]Stopped playing ".format(self.name).postln;
				};
				self[\playProcess].play;
				self.changed(\startedPlaying);
			},
			getCurrentSoundFileDuration: {|self|
				self.player.soundFileDurations[
					self.currentSoundFile
				]
			},
			playRandomSoundFile: {|self|
				var pathName;
				var duration;
				pathName = self.player.findSoundFilePathName;
				duration = self.player.soundFileDurations[pathName.fullPath];
				"Playing random sound file: % %".format(pathName, duration).postln;
				self.playSoundFile( pathName, duration );
			},
			isPlaying: false,
			currentSoundFile: "",
			synth: nil, //the synth that plays the sound file
			buffer: nil, //either the soundfile read buffer or the buffer
			playSoundFile: {|self, soundFilePathName, duration, fadeInTime|
				self.soundFileStrategies[self.strategy].playSoundFile(
					soundFilePathName, duration, fadeInTime
				);
			},
			stopSoundFile: {|self, fadeOutTime|
				self.soundFileStrategies[self.strategy].stopSoundFile(
					fadeOutTime
				)
			},
			endLoop: {|self|
				self[\willLoop] = false;
				self.changed('endedLoop');
			},
			strategy: \fromdisk,
			soundFileStrategies: (
				fromdisk: (
					playSoundFile: {|self, soundFilePathName, duration, fadeInTime|
						if(File.exists(soundFilePathName.fullPath), {
							fork{
								var soundFile = SoundFile.openRead(
									soundFilePathName.fullPath
								);
								var synthDefName = "diskPlayer%".format(
									soundFile.numChannels
								).asSymbol;
								soundFile.close;
								self[\buffer] = Buffer.cueSoundFile(
									runtime[\server], soundFilePathName.fullPath,
									soundFile.numChannels,
									bufferSize: 65536
								);
								runtime[\server].sync;
								self[\currentSoundFile] = soundFilePathName.fileName;
								self.changed(\currentSoundFile);
								"PlayingSoundFile: % dur: %".format(
									soundFilePathName, duration
								).postln;
								self[\synth] = Synth(
									synthDefName,
									[\bufnum, self[\buffer].bufnum, \amp, 0.4]
								);
								"Playing with the synth: %".format(self[\synth]).postln;
								self[\isPlaying] = true;
								self.changed(\isPlaying);
								fork{
									duration.wait;
									self.stopSoundFile(2);
								}
							}
						}, {
							"Sound file not found: %".format(soundFilePathName).warn;
						});
					},
					stopSoundFile: {|self, fadeOut|
						self[\synth].release;
						self[\buffer].close;
						self[\buffer].free;
						self[\isPlaying] = false;
						self.changed(\isPlaying);
					}
				)
			)
		);
		runtime = (
			timevars: timevars,
			setTimevarValue: {|self, letter, val|
				var timevar = self[\timevars][letter];
				timevar[\val] = timevar[\spec].constrain(val);
				timevar.changed(\val);
				"Timevar '%' val: %".format(letter, val).postln;
			},
			isPlaying: false,
			setPlay: {|self, aBool|
				if(aBool, {
					self.prStartPlaying();
				}, {
					self.prStopPlaying();
				});
			},
			runLoop: nil,
			prStartPlaying: {|self|
				if(self[\isPlaying].not, {
					self[\isPlaying] = true;
					self[\runLoop] = Routine({

					});
					self.changed(\isPlaying);
					"Started Playing".postln;
				}, {
					"Already playing".postln;
				});
			},
			prStopPlaying: {|self|
				if(self[\isPlaying], {
					self[\isPlaying] = false;
					self[\runLoop].stop;
					self.changed(\isPlaying);
					"Stopped playing".postln;
				}, {
					"Already stopped".postln;
				});
			},
			mode: \presence,
			setMode: {|self, val|
				if([\absence, \presence].includes(val), {
					if(self[\mode] != val, {
						self[\mode] = val;
						switch(self[\mode],
						\absence, {
							"Absence mode started".postln;
						},
						\presence, {
							"Presence mode started".postln;
						});
						self.changed(\mode);
					})
				}, {
					"Unknown mode: '%'".format(val).postln;
				});
			},
			masterVolume: 0.0,
			setMasterVolume: {|self, vol|
				self[\masterVolume] = vol;
				self.changed(\masterVolume);
			},
			musikklyd: (
				setSoundFileFolder: {|self, path|
					var soundBankLetters = Set.new;
					var soundBankSelector;
					self.proto[\setSoundFileFolder].value(self, path);
					self[\soundFilePathNames].do({|p|
						var letter = p.fileName.asString.first;
						soundBankLetters.add(letter);
					});
					soundBankLetters = soundBankLetters.asArray.sort;
					self[\soundBankNames] = soundBankLetters;
					"These are the sound bank letters: %".format(
						self[\soundBankNames]
					).postln;
					soundBankSelector = Pn(
						Plazy({
							Pshuf(self.soundBankNames, 1)
						})
					).asStream;
					self[\getNextSoundBank] = {
						soundBankSelector.next;
					};
					self[\currentSoundBank] = self.getNextSoundBank;
				},
				playerType: \musikklyd,
				soundFileNamePattern: "^[A-Z]_[MH][A-Z]_\\d+\\.wav$",
				channels: [
					( name: 'M1').proto_(playerChannelProto.deepCopy), //Melodi
					( name: 'M2').proto_(playerChannelProto.deepCopy), //Melodi
					( name: 'H1').proto_(playerChannelProto.deepCopy) //Harmoni/Effekt
				],
				findSoundFilePathName: {|self|
					var sfPattern;
					var roleKey;
					var fileName;
					roleKey = (melody: 'M', harmony: 'H')[self.role];
					sfPattern = "^%_%._\\d+\\.wav$".format(
						self[\currentSoundBank],
						roleKey
					);
					self[\soundFilePathNames].postln;
					sfPattern.postln;
					roleKey.postln;
					self.postln;
					fileName = self[\soundFilePathNames].select({arg pathName;
						sfPattern.matchRegexp(pathName.fileName);
					}).choose;
					"Found this filename: %".format(fileName).postln;
					fileName;
				},
				role: {|self|
					var result;
					var prefix = self[\name].asString.first;
					"Name: %".format(self[\name]).postln;
					"Prefix: %".format(prefix).postln;
					switch(prefix, 
						$M, {
							result = \melody;
						},
						$H, {
							result = \harmony;
						}
					);
					"result: %".format(result).postln;
					result;
				}
			).proto_(playerProto.deepCopy),
			fosselyd: (
				soundFileNamePattern: "^\\d+\\.wav$",
				playerType: \fosselyd,
				channels: [
					( name: "A").proto_(playerChannelProto.deepCopy),
					( name: "B").proto_(playerChannelProto.deepCopy)
				],
				findSoundFilePathName: {|self|
					var pathName;
					self[\soundFilePathNames].choose;
				},
			).proto_(playerProto.deepCopy),
			presenceSensor: true,
			setPresenceSensor: {|self, val|
				if(self[\presenceSensor] != val, {
					if(val, {
						self[\presenceSensor] = true;
						"Someone arrived".postln;
					}, {
						self[\presenceSensor] = false;
						"Someone left".postln;
					});
					self.changed(\presenceSensor);
				});
			},
			sensorMuted: true,
			muteSensor: {|self,val|
				"Muting sensor: %".format(val).postln;
				self[\sensorMuted] = val;
				self.changed(\sensorMuted);
			},
			stopProgram: {|self|
				"Stopping Fossegrim".postln;
				self[\shutdownProcess] = fork{
					var cond = Condition.new;
					var serverQuitMaxWaitProcess;
					//Stop synth, free buffers, stop server etc.
					if(self[\server].notNil, {
						var serverDidQuit = false;
						var server = self[\server].quit(
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
			},
			getFolderSizeInMB: {|self, path|
				var result;
				if(File.exists(path), {
					var stdout = "du -m % | awk '{ print $1 }'".format(
						path
					).unixCmdGetStdOut.drop(-1);//drop the line end
					if("^\\d+$".matchRegexp(stdout), {
						result = stdout.asInteger;
					});
				});
				if(result.isNil, {
					"Failed to get the folder size for '%'".format(
						path
					).warn;
				});
				result;
			},
			oscResponders: List[],
			startOSC: {|self|
				self[\oscResponders].addAll([
					OSCFunc({|msg, addr, time, port|
						var val;
						val = msg[1].booleanValue;
						self.setPresenceSensor(val);
					}, '/fossegrimen/presenceSensor'),
					OSCFunc({|msg, addr, time, port|
						var val;
						val = msg[1].booleanValue;
						self.setPlay(val);
					}, '/fossegrimen/play'),
					OSCFunc({|msg, addr, time, port|
						var val;
						val = msg[1].asSymbol;
						self.setMode(val);
					}, '/fossegrimen/mode'),
					OSCFunc({|msg, addr, time, port|
						var val;
						val = msg[1].booleanValue;
						self.muteSensor(val);
					}, '/fossegrimen/muteSensor')
				]);
			},
			initialize: {|self|
				"Initializing Fossegrim".postln;
				self[\server] = ~server;
				self.fosselyd.initialize(self);
				self.musikklyd.initialize(self);
				self.fosselyd.setSoundFileFolder(
					~config["fosselyderFolder"].standardizePath
				);
				self.musikklyd.setSoundFileFolder(
					~config["musikklyderFolder"].standardizePath
				);
				if(args[\startPlayAfterInit], {
					"Starting play after init".postln;
				});
				self.startOSC;
			}
		);
		runtime.initialize;
		~runtime = runtime;
	};
)
