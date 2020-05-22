FossegrimenPlayerChannel {
	var <name;
	var <player;
	var <willLoop = false;
	var playProcess;
	var <isPlaying = false;
	var <currentSoundFilePathName;
	var synth;
	var group;
	var buffer;
	var soundFileStrategies;
	var <elapsedSeconds;
	var elapsedSecondsResponder;

	*new{|name, player|
		^super.new.init(name, player);
	}

	init{|name_, player_|
		name = name_;
		player = player_;
		group = player.channelsGroup;
		this.prInitStrategies;
	}

	prInitStrategies{
		soundFileStrategies = (
			fromdisk: (
				startSynth: {
					|
					channel, soundFilePathName, buffer,
					duration, fadeInTime, action
					|
					//"Playing sound file from disk".postln;
					{DiskIn.ar(buffer.numChannels, buffer.bufnum).poll}.play;
				},
				stopSynth: {
					|
					channel, synth, fadeOutTime, action
					|
					//"Stopping sound file from disk".postln;
					synth.free();
					synth.server.sync;
					action.value;
				}
			),
			fromRAM: (
				startSynth: {
					|
					channel, soundFilePathName, buffer,
					duration, fadeInTime, fadeOutTime
					|
					var synth, bundle;
					//"Starting sound file from RAM".postln;
					bundle = player.server.makeBundle(false, {
						synth = Synth(
							"bufferPlayer%".format(buffer.numChannels).asSymbol,
							[
								\bufnum, buffer.bufnum,
								\amp, player.volumeBus.asMap,
								\attack, fadeInTime ? 0.01,
								\release, fadeOutTime ? 0.01,
								\duration, duration
							],
							group,
							\addToTail
						);
						synth.register;
					});
					player.server.listSendBundle(0.2, bundle);
					synth.server.sync;
					synth;
				},
				stopSynth: {
					|
					channel, synth, fadeOutTime, action
					|
					//"Stopping sound file from RAM".postln;
					player.server.makeBundle(0.2, {
						synth.onFree({
							action.value;
						});
						synth.release(fadeOutTime);
					});
				}
			)
		)
	}

	getCurrentSoundFileDuration{
		var result;
		if(currentSoundFilePathName.notNil, {
			result = player.soundFilesDurations[currentSoundFilePathName.fileName];
		});
		^result;
	}

	playRandomSoundFile{|fadeInTime, fadeOutTime, onStop, onPlay, onFailure|
		var pathName;
		var duration;
		pathName = player.getRandomSoundFilePathNameForChannel(this);
		duration = player.getSoundFileDuration(pathName.fullPath);
		//"Playing random sound file: % %".format(pathName, duration).postln;
		this.playSoundFile(
			pathName, duration, fadeInTime, fadeOutTime,
			onStop, onPlay, onFailure
		);
	}

	currentSoundFilePathName_{|pathName|
		currentSoundFilePathName = pathName;
		this.changed(\currentSoundFilePathName);
		this.changed(\duration);
		this.elapsedSeconds_(0.0);
	}

	currentSoundFileName{
		var result;
		if(currentSoundFilePathName.notNil, {
			result = currentSoundFilePathName.fileName;
		});
		^result;
	}

	playSoundFile{
		|
		soundFilePathName, duration, fadeInTime,
		fadeOutTime, onStop, onPlay, onFailure
		|
		forkIfNeeded{
			var cond = Condition.new;
			var buffer;
			var failToLoadBufferProcess;
			var bufferDidLoad = false;
			//if a synth is playing we free that and forget about it
			if(synth.isPlaying, {
				var oldSynth = synth;
				var oldSoundFilePathName = currentSoundFilePathName.copy;
				oldSynth.release;
				oldSynth.onFree({
					this.freeSoundFile(
						oldSoundFilePathName
					);
				})
			});
			player.allocBufferForSoundFilePathName(
				soundFilePathName,
				action: {|b|
					buffer = b;
					bufferDidLoad = true;
					cond.test = true;
					cond.signal;
				}
			);
			failToLoadBufferProcess = fork{
				//this is the max wait time until we call it a buffer load failure
				//The default latency for OSC message to scsynth is 0.2,
				//so we give it 300 ms before calling it failed.
				0.5.wait; 
				cond.test = true;
				cond.signal;
			};
			cond.wait;
			if(bufferDidLoad, {
				this.currentSoundFilePathName_(soundFilePathName);
				synth = this.prGetStrategyFunction(\startSynth).value(
					this, soundFilePathName, buffer, duration, fadeInTime,
					fadeOutTime
				);
				onPlay.value((
					synth: synth,
					duration: duration,
					soundFilePathName: soundFilePathName
				));
				if(elapsedSecondsResponder.notNil, {
					elapsedSecondsResponder.free;
					elapsedSecondsResponder.remove;
				});
				elapsedSecondsResponder = OSCFunc(
					{|msg, time, addr, port|
						this.elapsedSeconds_(msg[3]);
					},
					path: '/elapsedSeconds',
					srcID: player.server.addr,
					argTemplate: [synth.nodeID]
				);
				synth.onFree({|node|
					//This tests if the synth stopped itself, and that
					//a new synth has not taken over the role as the
					//sound file playing synth.
					if(node === synth, {
						if(isPlaying, {
							this.stopSoundFile(stopSynth: false, onStop: onStop);
						})
					});
				});
				isPlaying = true;
				this.changed(\isPlaying);
			}, {
				//If the buffer didn't load, or the confirm message from the
				//server somehow was lost, it is deemed that the buffer didn't
				//load. In either case we free the buffer in order to cover both
				//cases.
				//The onFailure callback can from the call site for this method
				//decide whether to retry the soundFile or not.
				"BUFFER DID NOT LOAD: %".format(soundFilePathName).warn;
				this.freeSoundFile(soundFilePathName);
				onFailure.value(this, soundFilePathName, buffer);
			});
		}
	}

	stopSoundFile{|fadeOutTime, action, stopSynth = true, onStop|
		forkIfNeeded{
			var cond = Condition.new;
			var pathName = currentSoundFilePathName;
			if(stopSynth, {
				this.prGetStrategyFunction(\stopSynth).value(
					this, synth, fadeOutTime, action: {
						cond.test = true;
						cond.signal;
					}
				);
				cond.wait;
			});
			this.freeSoundFile(pathName);
			this.currentSoundFilePathName_(nil);
			this.elapsedSeconds_(nil);
			onStop.value();
			isPlaying = false;
			this.changed(\isPlaying);
		}
	}

	freeSoundFile{|pathName|
		forkIfNeeded{
			if(pathName.notNil, {
				player.freeBufferForSoundFilePathName(pathName);
			});
		}
	}

	prGetStrategyFunction{|methodName|
		^soundFileStrategies.at(
			player.runtime.soundFileStrategy
		).at(
			methodName
		);
	}

	startLoop{}

	endLoop{
		willLoop = false;
		this.changed(\endedLoop);
	}

	elapsedSeconds_{|val|
		elapsedSeconds = val;
		this.changed(\elapsedSeconds);
	}

	duration{
		if(currentSoundFilePathName.notNil, {
			^player.getSoundFileDuration(
				currentSoundFilePathName.fullPath
			);
		}, {
			^nil;
		})
	}

	free{
		synth.free;
	}
}
