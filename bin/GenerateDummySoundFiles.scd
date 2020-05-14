//A script that generates dummy sound files for testing the installation
//This script will run only if the folder ~/sounds/fosselyder and
//~/sounds/musikklyder are empty
(
	var soundFileFolder = "~/sounds/fossegrimen2".standardizePath;
	var fosselyderFolder = soundFileFolder +/+ "fosselyder";
	var musikklyderFolder = soundFileFolder +/+ "musikklyder";
	File.mkdir(fosselyderFolder);
	File.mkdir(musikklyderFolder);

	//Generate the musikklyder sound files and put them into the folder
	{
		var sfNames = [];
		"ABCDEFGHIJKL".do({arg bankLetter;
			"MH".do({arg roleLetter; //melody and harmony
				"OGHF".do({arg instrumentLetter; //orgel, gitar, fele, harpeleik
					rrand(0, 20).do({arg fileNumber;
						sfNames = sfNames.add("%_%%_%.wav".format(
							bankLetter,
							roleLetter,
							instrumentLetter,
							fileNumber + 1
						));
					});
				});
			});
		});
		~soundfileGenerator = fork{
			//Only generate soundfiles if the folder is empty
			if(PathName(musikklyderFolder).entries.isEmpty, {
				sfNames.do({arg sfName;
					var score;
					var server = Server(\nrt,
						options: ServerOptions.new.numOutputBusChannels_(2)
					);
					var duration = rrand(2,10.0);
					var sfPath = "%/%".format(musikklyderFolder, sfName);
					var synth = Synth.basicNew(\dummySine, server, 1000);
					score = Score([
						[0.0, ['/d_recv',
							SynthDef(\dummySine, {|freq = 440|
								var sig;
								sig = SinOsc.ar([freq, freq + 2.0], mul: 0.2);
								Out.ar(0, sig);
							}).asBytes ]
						],
						[0.0, synth.newMsg( args: [\freq, exprand(100.0, 1200.0)])],
						[duration, synth.freeMsg]
					]);
					"Generating dummy sound file: '%'".format(sfPath).postln;
					//score.play(server);
					score.recordNRT(
						outputFilePath: sfPath,
						headerFormat: "wav",
						sampleFormat: "int16",
						options: server.options,
						duration: duration,
						action: {"Recording dummy sound file done: '%'".format(
							sfPath
						).postln;}
					);
					server.remove;
				});
				"DONE generating musikklyder soundfiles".postln;
			}, {
				"Musikklyder folder '%' not empty, not generating sound files".format(
					musikklyderFolder
				).postln;
				PathName(musikklyderFolder).entries.isEmpty.postln;
			});
			if(PathName(fosselyderFolder).entries.isEmpty, {
				//Generate the fosslyd sound files
				(1..20).do({arg sfName;
					var score;
					var server = Server(\nrt,
						options: ServerOptions.new.numOutputBusChannels_(2)
					);
					var duration = rrand(60.0, 240.0);
					var sfPath = "%/%.wav".format(fosselyderFolder, sfName);
					var synth = Synth.basicNew(\dummyNoise, server, 1000);
					score = Score([
						[0.0, ['/d_recv',
							SynthDef(\dummyNoise, {
								var filterFreqs = {exprand(100, 4000.0)} ! 3;
								var sig = PinkNoise.ar( 1.0 ! 2 );
								filterFreqs.do({|freq|
									sig = BPeakEQ.ar(sig, freq, 10.0, -10.0);
								});
								Out.ar(0, sig);
							}).asBytes
						]],
						[0.0, synth.newMsg( args: [\freq, exprand(100.0, 1200.0)])],
						[duration, synth.freeMsg]
					]);
					"Generating dummy sound file: '%'".format(sfPath).postln;
					//score.play(server);
					score.recordNRT(
						outputFilePath: sfPath,
						headerFormat: "wav",
						sampleFormat: "int16",
						options: server.options,
						duration: duration,
						action: {"Recording dummy sound file done: '%'".format(
							sfPath
						).postln;}
					);
					server.remove;
				});
				"DONE generating fosslyder soundfiles".postln;
			}, {
				"Fosselyder folder '%' not empty, not generating sound files".format(
					fosselyderFolder
				).postln;
			});
		}
	}.value;
)


PathName("~/sounds/fosselyder".standardizePath).entries.isEmpty
