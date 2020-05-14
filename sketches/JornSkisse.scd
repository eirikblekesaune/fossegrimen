NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/presenceSensor', false);
NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/presenceSensor', true);

NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/play', false);
NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/play', true);

NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/mode', \presence);
NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/mode', \absence);

NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/muteSensor', false);
NetAddr("127.0.0.1", 57121).sendMsg('/fossegrimen/muteSensor', true);


1.0.asTimeString(dropDaysIfPossible: false).drop(3).drop(-4)
s.boot;
Server.default.makeView
(
SynthDef(\help_Buffer_cue,{ arg out=0,bufnum;
    Out.ar(out,
        DiskIn.ar( 1, bufnum )
    )
}).add;
)

(
fork{
	var cond = Condition.new;
	b = Buffer.cueSoundFile(s, 
		Platform.resourceDir +/+ "sounds/a11wlk01.wav", 0, 1,
		completionMessage: {|buf|
			"AHA: %".format(buf).postln;

			y = Synth(\help_Buffer_cue, [\bufnum, buf], s);
			cond.test = true;
			cond.signal;
		}
	);
	cond.wait;
}
);
(
s.makeBundle(nil, {
    b = Buffer.cueSoundFile(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav", 0, 1);
    y = Synth(\help_Buffer_cue, [\bufnum, b], s);
});
)
b.close; b.free; y.free;

// make a window and embed a ServerMeterView inside of it.
(
	w = Window.new("Server Levels").layout_(
		HLayout(
			ServerMeterView.new(s, nil, 0@0, 0, 2);
		)
	);
	w.front; // show the window
)
s.boot
s.quit
(
	fork{
		var cond;
		var soundFile;
		var soundFilePath = "~/sounds/fossegrimen/fosselyder/1.wav".standardizePath;
		if(File.exists(soundFilePath).not, {
			"File not found: %".format(soundFilePath).postln;
			thisProcess.stop;
		});
		soundFile = SoundFile.openRead(soundFilePath);
		if(soundFile.isNil, {
			"Failed to open sound file: %".format(soundFilePath).postln;
		});
		soundFile.close;
		cond = Condition.new;
		"1111111".postln;
		[1,2].do({|numChannels|
			SynthDef("diskPlayer%".format(numChannels).asSymbol, {
				|
				out= 0,amp = 0.1, bufnum, gate=1,
				attack = 0.01, release= 0.01, rate = 1
				|
				var sig, env;
				env = Linen.kr(gate, attack, releaseTime: release);
				sig = VDiskIn.ar(
					numChannels, bufnum, rate: BufRateScale.kr(bufnum) * rate
				);
				sig.poll(label: \diskPlayer);
				Out.ar(out, sig * env * amp);
			}).add;
			s.sync;
			SynthDef("diskIn%".format(numChannels).asSymbol, {
				|
				out, amp=1, bufnum, sustainTime, atk=0, rel=0, gate=1, rate =1
				|
				var sig = VDiskIn.ar(numChannels, bufnum, BufRateScale.kr(bufnum * rate));
				var gateEnv = EnvGen.kr(Env([1, 1, 0], [sustainTime-rel, 0]));
				var env = EnvGen.kr(
					Env.asr(atk, 1, rel), gate * gateEnv, doneAction: Done.freeSelf
				);
				sig.poll(label: \diskIn);
				Out.ar(out, sig * env * amp)
			}).add;
			s.sync;
		});
		if(a.notNil, {
			a.free;
		});
		if(b.notNil and: {b.isKindOf(Buffer)}, {
			b.close;
			b.free({
				"Freed old bffer".postln;
				cond.test = true;
				cond.signal;
			});
		});
		cond.test = false;
		b = Buffer.cueSoundFile(
			s, soundFilePath,
			numChannels: soundFile.numChannels,
			bufferSize: 65536,
			completionMessage: {|...args|
				"Was loaded: %".format(args).postln;
				cond.test = true;
				cond.signal;
			}
		);
		cond.wait;
		fork{
			"Ready to play file".postln;
			a = Synth(
				"diskIn%".format(b.numChannels).asSymbol,
				//"diskPlayer%".format(b.numChannels).asSymbol,
				[\bufnum, b.bufnum, \amp, 0.5, \out, 0, \sustainTime, 3]
			);
		}
	};
)
(
	fork{
		"Ready to play file".postln;
		a = Synth(
			"diskIn%".format(b.numChannels).asSymbol,
			//"diskPlayer%".format(b.numChannels).asSymbol,
			[\bufnum, b.bufnum, \amp, 0.5, \out, 0, \sustainTime, 3]
		);
	}
)
b
s.boot;
s.meter
b.close;
b.free;
(
	b = Buffer.cueSoundFile(
		s, "~/sounds/fossegrimen/fosselyder/1.wav".standardizePath,
		//		numChannels: 2,
		//		bufferSize: 262144
	);
)
a.free;
(
	a = Synth(
		"diskIn%".format(b.numChannels).asSymbol,
		//"diskPlayer%".format(b.numChannels).asSymbol,
		[\bufnum, b.bufnum, \amp, 0.5, \out, 0, \gate, 1, \sustainTime, 10]
	);
)
a.release(4)
(

	[1,2].do({|numChannels|
		SynthDef("diskPlayer%".format(numChannels), {
			|
			out= 0,amp = 0.1, bufnum, gate=1,
			attack = 0.01, release= 0.01
			|
			var sig, env;
			env = Linen.kr(gate, attack, releaseTime: release);
			sig = VDiskIn.ar(numChannels, bufnum, rate: BufRateScale.kr(bufnum));
			Out.ar(out, sig * env * amp);
		}).add;
	});
)
(
	SynthDef(\diskIn2, { |out, amp=1, bufnum, sustainTime, atk=0, rel=0, gate=1|
		var sig = VDiskIn.ar(2, bufnum, BufRateScale.kr(bufnum));
		var gateEnv = EnvGen.kr(Env([1, 1, 0], [sustainTime-rel, 0]));
		var env = EnvGen.kr(
			Env.asr(atk, 1, rel), gate * gateEnv, doneAction: Done.freeSelf
		);
		Out.ar(out, sig * env * amp)
	}).add;
)
a

(
	//Alle banker må spilles av før samme bank kan spilles fra igjen
	var soundBanks = Array.newFrom(($A.ascii..$L.ascii).asAscii);
	var soundBankSelector = Pn(Plazy({Pshuf(soundBanks, 1)})).asStream;
	~getNextSoundBank = {
		soundBankSelector.next;
	};
)

(
	~sfNames = [];
	"ABCDEFGHIJKL".do({arg bankLetter;
		"MH".do({arg roleLetter; //melody and harmony
			"OGHF".do({arg instrumentLetter; //orgel, gitar, fele, harpeleik
				rrand(0, 15).do({arg fileNumber;
					~sfNames = ~sfNames.add("%_%%_%.wav".format(
						bankLetter,
						roleLetter,
						instrumentLetter,
						fileNumber + 1
					));
				});
			});
		});
	});
)
(
	~soundFileDurations = Dictionary[];
	~musikklyderPathNames = List[];
	~fosselyderPathNames = List[];
	//Build a list of all sound file path names.
	[
		(
			folderPath: "~/sounds/fossegrimen/musikklyder".standardizePath,
			what: 'musikklyd',
			result: ~musikklyderPathNames,
			pattern: "^[A-Z]_[MH][A-Z]_\\d+\\.wav$"
		),
		(
			folderPath: "~/sounds/fossegrimen/fosselyder".standardizePath,
			what: 'fosselyd',
			result: ~fosselyderPathNames,
			pattern: "^\\d+\\.wav$"
		),
	].do({arg item;
		var folder = item[\folderPath];
		var result = item[\result];
		var what = item[\what];
		var pattern = item[\pattern];
		if(File.exists(folder), {
			var path = PathName(folder);
			//Assuming that all sound files are in the same folder,
			//i.e. no recursive search.

			//Only find those that match the file pattern
			path.files.select({arg pathName;
				pattern.matchRegexp(pathName.fileName);
			}).do({arg pathName;
				var soundFile;
				"Found '%' file: '%'".format(what, pathName).postln;
				result.add(pathName);
				soundFile = SoundFile.openRead(pathName.fullPath);
				~soundFileDurations.put(pathName.fullPath, soundFile.duration);
				soundFile.close;
			});
		}, {
			"'%' folder not found at path: %".format(what, folder).warn;
		});
	});
)
~fosselyderPathNames
~soundFileDurations
(
	var playerProto = (
		willLoop: false,
		playProcess: Routine{},
		startLoop: {|self|
			"Starting loop for % of role: %".format(
				self.name, self.role
			).postln;
			self[\willLoop] = true;
			self[\playProcess] = Routine{
				while({self[\willLoop]}, {
					var pathName = self.findSoundFilePathName;
					var duration = ~soundFileDurations[pathName.fullPath];
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
		playSoundFile: {|self, soundFilePathName, duration|
			var soundFile = SoundFile(soundFilePathName.fullPath);
			"PlayingSoundFile: % dur: %".format(soundFilePathName, duration).postln;
			soundFile.cue((amp: 0.3), playNow: true);

			self.changed(\playingSoundFile, soundFilePathName);
		},
		endLoop: {|self|
			self[\willLoop] = false;
			self.changed('endedLoop');
		},
		findSoundFilePathName: {|self|
			var sfPattern;
			var roleKey;
			var fileName;
			roleKey = (melody: 'M', harmony: 'H')[self.role];
			sfPattern = "^%_%._\\d+\\.wav$".format(
				~currentSoundBank,
				roleKey);
			fileName = ~musikklyderPathNames.select({arg pathName;
				sfPattern.matchRegexp(pathName.fileName);
			}).choose;
			fileName;
		},
		role: {|self|
			var result;
			var prefix = self[\name].asString.first;
			switch(prefix,
				$M, {result = \melody;},
				$H, {result = \harmony;}
			);
			result;
		}
	);
	~players = Dictionary[
		1 -> (name: 'M1').proto_(playerProto.deepCopy),
		2 -> (name: 'M2').proto_(playerProto.deepCopy),
		3 -> (name: 'H1').proto_(playerProto.deepCopy)
	];
	~startMusikklyd = {
		//choose sound bank
		~currentSoundBank = ~getNextSoundBank.value;
		~musikkLoop = ~musikkLoop ?? {Routine{};};
		if(~musikkLoop.isPlaying.not, {
			~musikkLoop = fork{
				var playerKeysToStart = Pshuf(~players.keys.asArray, 1).asStream;
				var nextPlayerKey = playerKeysToStart.next;
				//this condition controls when the ending of musikklyd
				//should start happening.
				~musikkLoopCond = Condition.new(false);
				while({nextPlayerKey.notNil}, {
					var player = ~players[nextPlayerKey];
					player.startLoop;
					1.0.wait;//timevar 'i'
					nextPlayerKey = playerKeysToStart.next;
				});
				~endAfterMaxMusikkDuration = fork{
					20.wait;//timevar 'h'
					"Ending musikklyd by max dur".postln;
					~endMusikklyd.value;
				};
				~musikkLoopCond.wait;

				"Ending musikklyd loop in % (timevar 'g') seconds".format(
					3 //timevar 'g'
				).postln;
				3.wait;
				~players.do(_.endLoop);
				"Froze musikkplayers, letting them play out".postln;
			};
			//runtime.changed(\musikklyd, \state, \ending);
		}, {
			"Musikkloop already playing".postln;
		});
	};
	~endMusikklyd = {
		"Ending musikklyd".postln;
		~musikkLoopCond.test = true;
		~musikkLoopCond.signal;
		~endAfterMaxMusikkDuration.stop;
	};
	~startMusikklyd.value;
)

~endMusikklyd.value;
s.boot;

"du -m % | awk '{print $1}'".format("~/sounds/fossegrimen/fosselyder").unixCmdGetStdOut.asInteger

