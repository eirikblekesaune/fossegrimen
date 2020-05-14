//knob view
(
	var knob;
	~buildKnobView = {
		|
		label, unitSize, font, color,
		spec, model, callback, updater
		|
		///
		var width = unitSize * 4;
		var height = unitSize * 6;
		var view = View();
		var widgets = (
			label: StaticText()
			.string_(label)
			.align_(\center)
			.font_(font)
			.fixedHeight_(height / 6.0)
			.background_(color.blend(Color.black, 0.3)),
			knob: Knob()
			.action_({|k| 
				callback.value(spec.map(k.value), model, spec);
			})
			.minHeight_(height / 2.0),
			numbox: NumberBox()
			.action_({|k| 
				callback.value(k.value, model, spec);
			})
			.fixedHeight_(height / 6.0)
			.align_(\right)
			.font_(font.size_(10)),
			valueLabel: StaticText()
			.string_(spec !? { spec.units } ?? { "" })
			.align_(\center)
			.font_(font)
			.fixedHeight_(height / 6.0)
		);
		view.layout_(
			VLayout(
				widgets[\label],
				widgets[\knob],
				HLayout(
					widgets[\numbox]
				).margins_([5,0]),
				widgets[\valueLabel]
			).margins_(0).spacing_(0)
		).fixedSize_(Size(width, height));
		updater = updater ? {|widgets, whoChanged, whatChanged|
			widgets[\knob].value_(
				spec.unmap(whoChanged[\val])
			);
			widgets[\numbox].value_(whoChanged[\val])
		};
		model.addDependant({|whoChanged, whatChanged, more|
			if(whoChanged === model, {
				if(whatChanged == \val, {
					{
						updater.value(widgets, whoChanged, whatChanged, more);
					}.defer;
				})
			})
		});
		view.background_(color);
	};
	knob = ~buildKnobView.value(
		"a", //label
		25, //unitSize 
		Font("Avenir", 12), //font
		Color.yellow.alpha_(0.5), //color
		ControlSpec(0.01, 360.0, \amp, units: \secs), // spec
		(val: 0), //model
		{|val, model, spec| //callback
			"Callback: %".format(val).postln;
			model.put(\val, spec.constrain(val));
			model.changed(\val);
		},
		nil, //update
	);
//	knob = ~buildKnobView.value(
//		"FOSSLYDER", //label
//		25, //unitSize 
//		Font("Avenir", 12), //font
//		Color.red.alpha_(0.5), //color
//		ControlSpec(-90, 6.0, \db, units: \dB), // spec
//		(val: 0), //model
//		{|val, model, spec| //callback
//			"Callback: %".format(val).postln;
//			model.put(\val, spec.constrain(val));
//			model.changed(\val);
//		},
//		nil, //update
//	);
	knob.front;
)
(
	~buildSmallToggleButton = {
		|
		label, unitSize, font, color,
		model, offValue, onValue
		|
		var width = unitSize * 4;
		var height = unitSize;
		var view = View();
		var widgets = ();

		widgets.put(\label, 
			StaticText()
			.string_(label)
			.font_(font)
		);
		widgets.put(\button,
			Button()
			.states_([
				["", color],
				["x", Color.black, Color.red ]
			])
		);
		view.layout_(
			HLayout(
				widgets[\label].fixedWidth_((width / 4) * 3),
				widgets[\button].fixedWidth_(width / 4)
			).margins_(0).spacing_(0)
		);
		view.fixedSize_(Size(width, height));
		view.background_(color);
		view.front;
	};
	~buildSmallToggleButton.value(
		"PLAY", 15, Font("Avenir", 12), Color.green.alpha_(0.4),
		(val: false)
	).front;
)
(

	~buildLargeToggleButton = {
		|
		label, unitSize, font, color,
		model, offValue, onValue
		|
		var width = unitSize * 8;
		var height = unitSize * 6;
		var view = View();
		var widgets = ();

		widgets.put(\label, 
			StaticText()
			.string_(label)
			.font_(font.size_(16))
			.align_(\center)
		);
		widgets.put(\button,
			Button()
			.states_([
				[offValue ? "OFF", Color.black, color.deepCopy.alpha_(0.1) ],
				[onValue ? "ON", Color.black, color.complementary]
			])
			.font_(font.size_(16).bold_(true))
		);
		view.layout_(
			VLayout(
				widgets[\label].fixedSize_(Size(width, height / 3)),
				widgets[\button].fixedSize_(Size(width, (height / 3) * 2))
			).margins_(0).spacing_(0)
		);
		view.fixedSize_(Size(width, height));
		view.background_(color);
		view.front;
	};
	~buildLargeToggleButton.value(
		"PLAY", 15, Font("Avenir", 12), Color.green.alpha_(0.4),
		(val: false)
	).front;
)
(
	var playerView;
	var player;
	var buildPlayerChannelView = {
		|
		player, channelName, unitSize, font, color
		|
		var view, widgets = ();
		view = View();
		widgets.put(\channelNameLabel,
			StaticText()
			.font_(font)
			.background_(color)
			.string_(channelName)
			.align_(\left)
		);
		widgets.put(\fileNameLabel,
			StaticText()
			.font_(font)
			.background_(color)
			.align_(\left)
			.string_("<sound file name>")
		);
		widgets.put(\playButton, 
			~buildSmallToggleButton.value(
				"PLAY", unitSize, font, color,
				(val: false)
			)
		);
		view.layout_(
			HLayout(
				widgets[\channelNameLabel].fixedWidth_(unitSize * 4),
				widgets[\fileNameLabel],
				widgets[\playButton]
			).margins_(0).spacing_(0)
		);
		view.fixedSize_(Size(unitSize * 20, unitSize));
		view;
	};
	~buildPlayerView = {
		|
		label, unitSize, font, color,
		model //the player
		|
		var width = unitSize * 24;
		var height = unitSize * 6;
		var view = View();
		var widgets = ();
		widgets.put(\volumeView, ~buildKnobView.value(
			"FOSS", //label
			unitSize, font, color,
			ControlSpec(-90, 6.0, \db, units: \dB), // spec
			(val: 0), //model TODO: connect to model
			{|val, model, spec| //callback TODO: make proper model 
				"Callback: %".format(val).postln;
				model.put(\val, spec.constrain(val));
				model.changed(\val);
			}
		));
		widgets.put(\folderSelectButton,
			Button()
			.states_([["Set folder", Color.black, color]])
			.font_(font)
			.action_({
				var dialog = FileDialog(
					{|folder| "You selected folder: %".format(folder).postln;},
					{"You cancelled setting folder".postln;},
					2, //file mode 'directory'
					0, //acceptMode 'opening'
				);
			});
		);
		widgets.put(\folderDisplayLabel,
			StaticText()
			.font_(font)
			.background_(color.blend(Color.white), 0.4)
			.string_("No folder")
		);
		widgets.put(\channelPlayButton, 
			~buildSmallToggleButton.value(
				"PLAY", unitSize, font, color,
				(val: false)
			)
		);
		widgets.put(\channelViews, 
			model.channels.keys.asArray.sort.collect({|channelKey|
				var chan = model.channels[channelKey];
				"Building chan: %".format(chan).postln;
				buildPlayerChannelView.value(
					model, chan.name, unitSize, font, color
				)
			})
		);

		view.layout_(
			HLayout(
				widgets[\volumeView],
				VLayout(
					HLayout(
						View().layout_(
							VLayout(
								HLayout(
									widgets[\folderSelectButton].maxWidth_(unitSize * 4),
									nil, 
									widgets[\channelPlayButton]
								) .spacing_(0).margins_(0),
								widgets[\folderDisplayLabel],
								//horz spanline trick
								View().fixedHeight_(1.0).background_(Color.black) 
							).margins_(0).spacing_(0)
						)
						.fixedSize_(Size(unitSize * 20, unitSize * 2))
						.background_(Color.rand.alpha_(0.5)), //widgets[\folderSelectView],
					).margins_(0).spacing_(0),
					VLayout(
						*widgets[\channelViews] ++ [nil]
					)
				)
			).margins_(0).spacing_(0)
		).fixedSize_(Size(width, height));
		view.background_(color);
		view;
	};
	player = (
		channels: Dictionary[
			1 -> (name: 'M1'), //Melodi
			2 -> (name: 'M2'), //Melodi
			3 -> (name: 'H1') //Harmoni/Effekt
		],
	);
	playerView = ~buildPlayerView.value(
		"FOSSELYDER",
		15, //unit Size
		Font("Avenir", 12), // font
		Color.blue.alpha_(0.1), //color
		player
	);
	playerView.front;
)

(
	//timevar preset view
	~buildDropMenuView = {
		|
		label, options, callback,
		unitSize, font, color
		|
		var view = View();
		var widgets = ();
		widgets.put(\menu, 
			PopUpMenu()
			.font_(font)
			.background_(color)
			.items_(options)
			.action_({|m|
				callback.value(m.item);
			})
		);
		view.layout_(
			VLayout(
				VLayout(
					StaticText()
					.string_(label)
					.font_(font)
					.align_(\center),
					nil,
				).margins_(4),
				widgets[\menu]
			).spacing_(0).margins_(0),
		);
		view.fixedSize_(Size(unitSize * 4, unitSize * 6));

		view.background_(color);
		view;
	};
	~buildDropMenuView.value(
		"Presets", ["aaa", "bbb", "ccc"],
		{|val| "Selected preset: '%'".format(val).postln;},
		15, Font("Avenir", 12), Color.blue.alpha_(0.1)
	).front


)
