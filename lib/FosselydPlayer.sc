FosselydPlayer : FossegrimenPlayer {
	var soundFileSelector;

	prInitChannels{
		soundFileSelector = Pn(
			Plazy({
				Pshuf(soundFilesPathNames, 1)
			})
		).asStream;
		channels.addAll([
			FossegrimenPlayerChannel(
				name: 'A',
				player: this
			),
			FossegrimenPlayerChannel(
				name: 'B',
				player: this
			)
		]);
	}

	getRandomSoundFilePathNameForChannel{|channel|
		^soundFileSelector.next;
	}

	soundFileNamePattern {
		^"^\\d+\\.wav$";
	}

	prStartPlaying{|settings|
		playProcess = Routine({
			var cond = Condition.new;
			var channelSelector = Pseq(
				channels, inf
			).asStream;
			var fadeInTimeSelector = Pn(Pfunc{
				runtime.getTimevarValue(\b)
			}, inf);
			if(settings.notNil , {
				if(settings.includesKey(\fadeInTime), {
					fadeInTimeSelector = Pseq([
						settings[\fadeInTime],
						fadeInTimeSelector.deepCopy
					]);
				});
			});
			fadeInTimeSelector = fadeInTimeSelector.asStream;
			loop{
				var currentChannel = channelSelector.next;
				var duration;
				var fadeInTime = fadeInTimeSelector.next;
				currentChannel.playRandomSoundFile(
					fadeInTime: fadeInTime,
					fadeOutTime: runtime.getTimevarValue(\a),
					onPlay: {|ev|
						duration = ev[\duration] - runtime.getTimevarValue(\a);
						cond.test = true;
						cond.signal;
					},
					onFailure: {|...args|
						"Buffer failed to load for: %".format(
							args
						).warn;
						duration = 0.1;
						cond.test = true;
						cond.signal;
					}
				);
				cond.wait;
				duration.wait;
			};
		}).play;
		super.prStartPlaying;
	}

	prStopPlaying{|settings|
		var fadeOutTime;
		if(settings.notNil and: {settings.includesKey(\fadeOutTime)}, {
			fadeOutTime = settings[\fadeOutTime];
		});
		playProcess.stop;
		channels.do({|channel|
			if(channel.isPlaying, {
				channel.stopSoundFile(fadeOutTime: fadeOutTime);
			});
		});
		super.prStopPlaying;
	}
}
