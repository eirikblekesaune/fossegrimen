(
	currentEnvironment.postln;
	runtime.postln;
	var buildTimevarCtrlView = {|letter, runtime|
		var v, knob, label, numbox;
		var model = runtime[\data][letter];
		v = View();
		knob = Knob()
		.action_({|knob|
			var spec = model[\spec];
			"Knob for % value: %".format(
				letter, model
			).postln;
			runtime.setTimevarValue(letter, spec.map(knob.value) );
		});

		numbox = NumberBox()
		.action_({|box|
			var spec = model[\spec];
			"Numbox for % value: %".format(
				letter, model
			).postln;
			runtime.setTimevarValue(letter, spec.map(box.value) );
		});
		label = StaticText().string_(letter);
		model.addDependant = {arg whoChanged, whatChanged;
			{
				if(whoChanged === model and: {whatChanged == \val}, {
					var spec = whoChanged[\spec];
					var newVal = spec.unmap(whoChanged[\val]);
					knob.value_(newVal);
					numbox.value_(newVal);
					label.value_(newVal);
				});
			}.defer;
		};
		v.layout_(VLayout(
			VLayout(
				label,
				numbox
			),
			knob
		));
		v;
	};
	var buildGui = {
		var gui = Window("Fossegrim");
		gui.view.layout_(

		);

		gui;
	};
)

