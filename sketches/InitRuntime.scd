(
	var args = currentEnvironment;
	var env = args[\env];
	env.use{
		var timevarSpec = {ControlSpec(0.0, 180, \lin)};
		var timevarLetters = ($a.ascii..$k.ascii).collect(_.asAscii).collect(_.asSymbol);
		var data = (
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
		var runtime = (
			data: data,
			timevarLetters: timevarLetters,
			setTimevarValue: {|self, letter, val|
				var timevar = self[\data][letter];
				timevar[\val] = val;
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
			mode: \absence,
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
						this.changed(\mode);
					})
				});
			},
			masterVolume: 0.0,
			setMasterVolume: {|self, vol|
				self[\masterVolume] = vol;
				self.changed(\masterVolume);
			},
			musikklyd: (
				players: Dictionary[
					1 -> (), //Melodi
					2 -> (), //Melodi
					3 -> () //Harmoni/Effekt
				],
				volume: 0, // 0 - 100
				setVolume: {}
			),
			fosselyd: (
				soundBanks: Array.newFrom(($A.ascii..$L.ascii).asAscii),

				players: Dictionary[
					1 -> (),
					2 -> (),
				],
				volume: 0 // 0 - 100
			),
			presenceSensor: false,
			setPresenceSensor: {|self, val|
				if(self[\presenceSensor] != val, {
					if(val, {
						self[\presenceSensor] = true;
						"Someone arrived".postln;
					}, {
						self[\presenceSensor] = false;
						"Someone left".postln;
					})
				});
			}
		);
		~runtime = runtime;
	};
)
