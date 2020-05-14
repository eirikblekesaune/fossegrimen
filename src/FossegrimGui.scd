(
	var args = currentEnvironment;
	var env = args[\env];

	//----------------------------------------------
	//----------GUI view builder functions---------- 
	var buildKnobView = {
		|
		label, unitSize, font, color,
		spec, model, callback, updater, listenTo
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
				if(whatChanged == (listenTo ? \val), {
					{
						updater.value(widgets, whoChanged, whatChanged, spec)
					}.defer;
				})
			})
		});
		view.background_(color);
	};
	var buildSmallToggleButton = {
		|
		label, unitSize, font, color,
		model, offValue, onValue,
		clickable,
		listenTo, callback
		|
		var width = unitSize * 4;
		var height = unitSize;
		var view = View();
		var widgets = ();
		var updater;

		widgets.put(\label, 
			StaticText()
			.string_(label)
			.font_(font)
		);
		widgets.put(\button,
			Button()
			.acceptsMouse_(clickable ? true)
			.canFocus_(clickable ? true)
			.action_({|butt|
				var val = butt.value.booleanValue;
				if(onValue.notNil and: {offValue.notNil}, {
					switch(val,
						true, {val = onValue;},
						false, {val = offValue;}
					);
				});
				callback.value(val)
			})
			.states_([
				["", color],
				["x", Color.black, Color.red ]
			])
		);
		updater = updater ? {|widgets, whoChanged, whatChanged|
			var buttonValue;
			var val = whoChanged[listenTo ? \val];
			if(onValue.isNil and: {offValue.isNil} and: {val.isKindOf(Boolean)}, {
				buttonValue = val.asInteger;
			}, {
				//assume we have set both on and off values here
				switch(val,
					onValue, {
						buttonValue = 1;
					},
					offValue, {
						buttonValue = 0;
					}
				);
			});
			widgets[\button].value_( buttonValue );
		};
		model.addDependant({|whoChanged, whatChanged, more|
			if(whoChanged === model, {
				if(whatChanged == (listenTo ? \val), {
					{
						updater.value(widgets, whoChanged, whatChanged);
					}.defer;
				})
			})
		});
		{updater.value(widgets, model, listenTo ? \val);}.defer;
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
	var buildLargeToggleButton = {
		|
		label, unitSize, font, color,
		model, offValue, onValue,
		offColor, onColor, clickable,
		listenTo, callback
		|
		var width = unitSize * 8;
		var height = unitSize * 6;
		var view = View();
		var widgets = ();
		var updater;

		widgets.put(\label, 
			StaticText()
			.string_(label)
			.font_(font.size_(16))
			.align_(\center)
		);
		widgets.put(\button,
			Button()
			.states_([
				[offValue ? "OFF", Color.black,
					offColor ? color.deepCopy.alpha_(0.1) ],
				[onValue ? "ON", Color.black,
					onColor ? color.complementary]
			])
			.font_(font.size_(16).bold_(true))
			.acceptsMouse_(clickable ? true)
			.canFocus_(clickable ? true)
			.action_({|butt|
				var val = butt.value.booleanValue;
				if(onValue.notNil and: {offValue.notNil}, {
					switch(val,
						true, {val = onValue;},
						false, {val = offValue;}
					);
				});
				callback.value(val)
			})
		);
		updater = updater ? {|widgets, whoChanged, whatChanged|
			var buttonValue;
			var val = whoChanged[listenTo ? \val];
			"updater: % %".format(val, listenTo).postln;
			if(onValue.isNil and: {offValue.isNil} and: {val.isKindOf(Boolean)}, {
				buttonValue = val.asInteger;
			}, {
				//assume we have set both on and off values here
				switch(val,
					onValue, {
						buttonValue = 1;
					},
					offValue, {
						buttonValue = 0;
					}
				);
			});
			widgets[\button].value_( buttonValue );
		};
		model.addDependant({|whoChanged, whatChanged, more|
			if(whoChanged === model, {
				if(whatChanged == (listenTo ? \val), {
					{
						updater.value(widgets, whoChanged, whatChanged);
					}.defer;
				})
			})
		});
		{updater.value(widgets, model, listenTo ? \val);}.defer;

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
	var buildPlayerChannelView = {
		|
		channel, unitSize, font, color
		|
		var view, widgets = ();
		view = View();
		widgets.put(\channelNameLabel,
			StaticText()
			.font_(font)
			.background_(color)
			.string_("CHANNEL: %".format(channel.name))
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
			buildSmallToggleButton.value(
				"PLAY", unitSize, font, color,
				channel, false, true,
				true,
				\isPlaying,
				{|val|
					if(val, {
						channel.playRandomSoundFile;
					}, {
						channel.stopSoundFile(0);
					});
				}
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
	var buildPlayerView = {
		|
		label, unitSize, font, color,
		model //the player
		|
		var width = unitSize * 24;
		var height = unitSize * 6;
		var view = View();
		var widgets = ();
		var makeFolderViewString = {
			var result;
			if(model[\soundFilesFolder].notNil, {
				result = "% (% MB)".format(
					model[\soundFilesFolder],
					model.getSoundFilesFolderSize
				);
			});
			result;
		};
		widgets.put(\volumeView, buildKnobView.value(
			label,
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
					{|f|
						var folder = f.first;
						"You selected folder: %".format(folder).postln;
						model.setSoundFileFolder(folder);
					},
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
			.string_(makeFolderViewString.value ? "<No folder>")
		);
		widgets.put(\channelPlayButton, 
			buildSmallToggleButton.value(
				"PLAY", unitSize, font, color,
				(val: false)
			)
		);
		widgets.put(\channelViews, 
			model.channels.collect({|channel|
				buildPlayerChannelView.value(
					channel, unitSize, font, color
				)
			})
		);

		SimpleController(model).put(\loadedSoundFilesFolder, {
			{
				widgets[\folderDisplayLabel].string_(
					makeFolderViewString.value
				);
			}.defer;
		});

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
	var buildDropMenuView = {
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
				widgets[\menu],
				nil
			).spacing_(0).margins_(0),
		);
		view.fixedSize_(Size(unitSize * 4, unitSize * 6));

		view.background_(color);
		view;
	};

	//----------------------------------------------
	//----------GUI setup and init views------------ 
	env.use{
		var runtime = ~runtime;
		var unitSize = args[\unitSize] ? 20;
		var font = args[\font] ? Font("Avenir", 12);
		var color = Color.yellow.alpha_(0.1);
		var buildTimevarCtrlView = {|letter, runtime|
			var v;
			var model = runtime[\timevars][letter];
			var updater = 
			v = buildKnobView.value(
				letter, unitSize, font, color,
				model.spec,
				model,
				{|val, model, spec|
					runtime.setTimevarValue(letter, val );
				}
			);
			v;
		};
		var widgets = ();
		var buildGui;
		widgets.put(\timevarPanel,
			HLayout(
				*(runtime[\timevars].keys.asArray.sort.collect({|letter|
					var timevar = runtime[\timevars][letter];
					buildTimevarCtrlView.value(letter, runtime);
				})) ++ [
					buildDropMenuView.value(
						"PRESETS",
						["aaa", "bbb", "ccc"],
						{|val| "Setting DA preset: '%'".format(val).postln;},
						unitSize, font, color
					)
				]
			)
		);
		widgets.put(\playersPanel, 
			HLayout(
				buildPlayerView.value(
					'FOSSELYD', unitSize, font, color,
					runtime[\fosselyd]
				),
				buildPlayerView.value(
					'MUSIKKLYD', unitSize, font, color,
					runtime[\musikklyd]
				)
			)
		);
		widgets.put(\bottomButtonPanel,
			HLayout(
				buildLargeToggleButton.value(
					"PLAY", unitSize, font, color,
					runtime, false, true,
					Color.red, Color.green, true,
					\isPlaying,
					{|val|
						runtime.setPlay(val);
					}
				),
				buildLargeToggleButton.value(
					"MODE", unitSize, font, color,
					runtime, 'absence', 'presence',
					Color.red, Color.green, true,
					\mode,
					{|val|
						runtime.setMode(val);
					}
				),
				buildLargeToggleButton.value(
					"SENSOR", unitSize, font, color,
					runtime, false, true,
					Color.red.alpha_(0.1), Color.green.alpha_(0.1), false,
					\presenceSensor
				),
				buildLargeToggleButton.value(
					"MUTE SENSOR", unitSize, font, color,
					runtime, false, true,
					Color.red, Color.green, true,
					\sensorMuted,
					{|val|
						runtime.muteSensor(val)
					}
				)
			)
		);
		buildGui = {
			var gui = Window("Fossegrim");
			var meterViewParent = View();
			var meterView = ServerMeterView(
				~server,
				meterViewParent, 0@0, 0, 2
			);
			gui.view.layout_(
				HLayout(
					VLayout(
						buildKnobView.value(
							"MASTER", unitSize, font, color,
							ControlSpec(-90.0, 6.0, \db, units: \dB),
							~server.volume,
							{|val|
								~server.volume = val;
							}.inEnvir, 
							{|widgets, whoChanged, whatChanged, spec|
								widgets[\knob].value_(
									spec.unmap(whoChanged.volume)
								);
								widgets[\numbox].value_(whoChanged.volume);
							},
							\amp
						),
						meterViewParent
					),
					VLayout(
						widgets[\timevarPanel],
						widgets[\playersPanel],
						HLayout(
							widgets[\bottomButtonPanel],
							VLayout(
								meterViewParent,
								StaticText().string_(
									"OSC Listening port: %".format(
										NetAddr.localAddr.port
									)
								)
							)
						)
					).margins_(0).spacing_(0)
				).margins_(0).spacing_(0.0)
			);
			gui;
		};
		~gui = buildGui.value;
		~gui.onClose_({
			if(args[\shouldExitOnClose], {
				runtime.stopProgram();
			});
		});
		//~gui.fullScreen;
		~gui.front;
	};


)
