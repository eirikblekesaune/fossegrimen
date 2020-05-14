(
var args = currentEnvironment;
var env = args[\env];
env.use{
	var runtime = ~runtime;
	var buildTimevarCtrlView = {|letter, runtime|
		var v, knob, label, numbox;
		var model = runtime[\data][letter];
		v = View();
		knob = Knob()
		.action_({|knob|
			var spec = model[\spec];
			runtime.setTimevarValue(letter, spec.map(knob.value) );
		});

		numbox = NumberBox()
		.action_({|box|
			runtime.setTimevarValue(letter, box.value);
		});
		label = StaticText().string_(letter);
		model.addDependant({arg whoChanged, whatChanged;
			{
				if(whoChanged === model and: {whatChanged == \val}, {
					var spec = whoChanged[\spec];
					var newVal = whoChanged[\val];
					knob.value_(spec.unmap(newVal));
					numbox.value_(newVal);
				});
			}.defer;
		});
		v.layout_(HLayout(
			label,
			VLayout(
				[knob, align: \right],
				HLayout(numbox, StaticText().string_("sek"))
			)
		));
		v.background_(Color.grey.alpha_(0.3));
		v;
	};
	var buildButtonView = {|label, runtime, setterFunc, listenTo, decor|
		var button = Button()
		.states_([["OFF"], ["ON"]])
		.action_({|butt|
			"% button val: %".format(label, butt.value).postln;
			setterFunc.value(runtime, butt.value.booleanValue);
		});
		runtime.addDependant({|whoChanged, whatChanged|
			if(whatChanged == listenTo, {
				{ button.value_(runtime[listenTo]); }.defer
			});
			decor.value(button, whoChanged, whatChanged);
		});
		View().layout_(HLayout(
			[StaticText().string_(label ? ""), align: \left],
			[button, align: \right]
		))
		.maxWidth_(200)
		.background_(Color.rand.alpha_(0.3))
	};
	var buildGui = {
		var gui = Window("Fossegrim", Rect(0, 0, 
			500,
			Window.screenBounds.height -50
		));
		var height, width;
		width = gui.bounds.width;
		height = gui.bounds.height;
		gui.view.layout_(
			HLayout(
				VLayout(
					*runtime[\timevarLetters].collect({|letter|
						buildTimevarCtrlView.value(letter, runtime)
						.fixedSize_(Size(150,height /runtime[\timevarLetters].size))
					})
				),
				VLayout(
					buildButtonView.value("PLAY", runtime,
						runtime[\setPlay],
						\isPlaying
					),
					buildButtonView.value("SENSOR PRESENCE", runtime,
						runtime[\setPresenceSensor],
						\presenceSensor,
						{|butt, who, what|
							if(what == \mode, {
								switch(runtime[\mode], 
								\presence, {
									"Button callbak Presence".postln;
								}, 
								\absence, {
									"Button callback Absence".postln;
								});
							})
						}
					),
					HLayout(
						StaticText().string_("MODE"),
						Button().states_([["MANUAL"],["SENSOR DRIVEN"]]),
						nil
					),
					nil,
					[StaticText().string_("VOL"), align: \bottom]
				)
			)
		);
		gui;
	};
	~gui = buildGui.value;
	~gui.front;
};
)
