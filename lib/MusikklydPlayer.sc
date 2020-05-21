MusikklydPlayer : FossegrimenPlayer {
	var soundBankSelector;
	var <soundBankNames;
	var <currentSoundBankName;
	var <subProcesses;
	var channelsStoppedListeners;

	prInitChannels{
		channels.addAll([
			FossegrimenPlayerChannel(
				name: 'M1',
				player: this
			),
			FossegrimenPlayerChannel(
				name: 'M2',
				player: this
			),
			FossegrimenPlayerChannel(
				name: 'H1',
				player: this
			)
		]);
	}

	soundFileNamePattern {
		^"^[A-Z]_[MH][A-Z]_\\d+\\.wav$";
	}

	*findRoleForChannel{|channel|
		var result;
		var prefix = channel.name.asString.first;
		switch(prefix, 
			$M, {
				result = \melody;
			},
			$H, {
				result = \harmony;
			}
		);
		^result;
	}

	getRandomSoundFilePathNameForChannel{|channel|
		var roleKey = (melody: 'M', harmony: 'H')[
			this.class.findRoleForChannel(channel)
		];
		var sfPattern;
		var fileName;
		sfPattern = "^%_%._\\d+\\.wav$".format(
			currentSoundBankName,
			roleKey
		);
		fileName = soundFilesPathNames.select({|p|
			sfPattern.matchRegexp(p.fileName);
		}).choose;
		^fileName;
	}

	soundFilesFolder_{|sfFolder|
		var soundBankLetters = Set.new;
		super.soundFilesFolder_(sfFolder);
		soundFilesPathNames.do({|p|
			var letter = p.fileName.asString.first;
			soundBankLetters.add(letter);
		});
		soundBankLetters = soundBankLetters.asArray.sort;
		soundBankNames = soundBankLetters;

		soundBankSelector = Pn(
			Plazy({
				Pshuf(soundBankNames, 1)
			})
		).asStream;
		this.goToNextBank;
	}

	currentSoundBankName_{|val|
		currentSoundBankName = val;
		this.changed(\currentSoundBankName);
	}

	getNextSoundBankName{
		^soundBankSelector.next;
	}

	goToNextBank{
		this.currentSoundBankName_(this.getNextSoundBankName);
	}

	prStartPlaying{|settings|
		if(channelsStoppedListeners.notNil, {
			this.prRemoveChannelsStoppedListeners;
		});
		playProcess = Routine({
			var channelSelector = Pshuf(channels, inf).asStream;
			this.goToNextBank;
			if(subProcesses.isNil, {
				subProcesses = IdentityDictionary.new;
			});
			channels.size.do{
				var selectedChannel;
				selectedChannel = channelSelector.next;
				subProcesses.put(selectedChannel.name, 
					fork{
						var cond = Condition.new;
						var duration;
						loop{
							cond.test = false;
							selectedChannel.playRandomSoundFile(
								onPlay: {|ev|
									duration = ev[\duration];
								},
								onStop: {|ev|
									cond.test = true;
									cond.signal;
								},
								onFailure: {|...args|
									"\tBuffer failed to load for: %. Will retry now.".format(
										args
									).warn;
									cond.test = true;
									cond.signal;
								}
							);
							cond.wait;
							runtime.getTimevarValue(\j).wait;
						}
					}
				);
				runtime.getTimevarValue(\i).wait;
			};
		}).play;
		super.prStartPlaying;
	}

	prStopPlaying{|settings|
		fork{
			var onAllStopped;
			var channelsArePlaying = Array.newClear(channels.size);
			var allChannelsStopped = Condition.new;
			if(settings.notNil, {
				if(settings.includesKey(\onAllStopped), {
					onAllStopped = settings[\onAllStopped];
				});
			});
			if(channelsStoppedListeners.notNil, {
				this.prRemoveChannelsStoppedListeners;
			}, {
				channelsStoppedListeners = List.new;
			});
			//We check isPlaying from the moment the stop call has been made
			// to avoid any subsequent stop calls to not interfere with the
			// current one. This is something that may occur if the modes are switched
			// with a short time interval.
			channels.do({|channel, i|
				var channelListener;
				var maxStopTime;
				var maxStopTimeProcess;
				channelsArePlaying.put(i, channel.isPlaying); //update the state

				channelListener = SimpleController(channel).put(\isPlaying, {
						channel.removeDependant(channelListener);
						channelsArePlaying.put(i, false);
						runtime.notify("Stopped channel: % test: % ".format(
							i, channelsArePlaying
						));
						maxStopTimeProcess.stop;
						allChannelsStopped.test = channelsArePlaying.every({|x| x == false});
						allChannelsStopped.signal;
					}
				);
				channelsStoppedListeners.add(channelListener);
			});
			playProcess.stop;
			subProcesses.do(_.stop);
			super.prStopPlaying;
			allChannelsStopped.wait;
			onAllStopped.value;
		}
	}

	prRemoveChannelsStoppedListeners{
		channels.do({|channel|
			channelsStoppedListeners.do({|listener|
				if(channel.dependants.includes(listener), {
					channel.removeDependant(listener);
				});
			});
		});
		channelsStoppedListeners = nil;
	}
}
