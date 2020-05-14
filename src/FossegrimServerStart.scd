(
	var args = currentEnvironment;
	var env = args[\env];
	env.use{
		var server = Server.default;
		var cond = Condition.new;
		var maxBootTimeChecker;
		var serverWasBooted = false;
		server.waitForBoot({
			serverWasBooted = true;
			cond.test = true;
			cond.signal;
		});
		maxBootTimeChecker = fork{
			10.wait; //wait maximum 10 seconds before giving up booting scserver
			cond.test = true;
			cond.signal;
		};
		cond.wait;
		if(serverWasBooted, {
			env[\server] = server;
			//Load SynthDefs
			[1,2].do({|numChannels|
				SynthDef("diskPlayer%".format(numChannels).asSymbol, {
					|
					out= 0,amp = 0.1, bufnum, gate=1,
					attack = 0.01, release= 0.01, rate = 1
					|
					var sig, env;
					env = Linen.kr(
						gate, attack, releaseTime: release,
						doneAction: 2
					);
					sig = VDiskIn.ar(
						numChannels, bufnum, rate: BufRateScale.kr(bufnum) * rate
					);
					Out.ar(out, sig * env * amp);
				}).add;
				server.sync;
				SynthDef("bufferPlayer%".format(numChannels).asSymbol, {
					|
					out= 0,amp = 0.1, bufnum, gate=1,
					attack = 0.01, release= 0.01, rate = 1
					|
					var sig, env;
					env = Linen.kr(
						gate, attack, releaseTime: release,
						doneAction: 2
					);
					sig = PlayBuf.ar(
						numChannels, bufnum, rate: BufRateScale.kr(bufnum) * rate
					);
					Out.ar(out, sig * env * amp);
				}).add;
				server.sync;
			});
		});
	};
)
