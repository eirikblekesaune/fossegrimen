FossegrimenView {

	*buildKnobView{
		|
		label, unitSize, font, color, spec,
		model, callback, listenTo, updater
		|
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
				spec.unmap(whoChanged.perform(listenTo ? \val))
			);
			widgets[\numbox].value_(whoChanged.perform(listenTo ? \val));
		};
		model.addDependant({|whoChanged, whatChanged, more|
			if(whoChanged === model, {
				if(whatChanged == (listenTo ? \val), {
					{
						updater.value(
							widgets, whoChanged, whatChanged, spec
						)
					}.defer;
				})
			})
		});
		updater.value(widgets, model, listenTo ? \val);
		view.background_(color);
		^view;
	}

	*buildSmallToggleButton{
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
			var val = whoChanged.perform(listenTo ? \val);
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
		^view;
	}

	*buildLargeToggleButton{
		|
		label, unitSize, font, color,
		model, offValue, onValue,
		offColor, onColor, clickable,
		listenTo, callback,
		postInitAction
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
					offColor ? color.deepCopy.alpha_(0.1)
				],
				[onValue ? "ON", Color.black,
					onColor ? color.complementary]
				]
			)
			.font_(font.size_(16).copy.bold_(true))
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
			var val = whoChanged.perform(listenTo ? \val);
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
		postInitAction.value(view, widgets);
		^view;
	}

	*buildPlayerChannelView{
		|
		channel, unitSize, font, color
		|
		var view, widgets = ();
		var makeMMSSString = {|seconds|
			var result;
			if(seconds.notNil, {
				result = seconds.asTimeString.drop(3).drop(-4)
			}, {
				result = "--:--"
			});
			result;
		};
		var makePlayPositionString = {|seconds, duration|
			"% / %".format(
				makeMMSSString.value(seconds),
				makeMMSSString.value(duration)
			);
		};
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
			.string_(
				channel.currentSoundFileName ? "<no sound file>"
			)
		);
		widgets.put(\playPositionLabel,
			StaticText()
			.font_(font)
			.background_(color)
			.align_(\right)
			.string_(
				makePlayPositionString.value(
					channel.elapsedSeconds,
					channel.duration
				)
			)
		);
		widgets.put(\playButton, 
			this.buildSmallToggleButton(
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
		SimpleController(channel).put(\currentSoundFilePathName, {
			{
				widgets[\fileNameLabel].string_(
					channel.currentSoundFileName ? "<no sound file>"
				)
			}.defer;
		});
		SimpleController(channel).put(\elapsedSeconds, {
			{
				widgets[\playPositionLabel].string_(
					makePlayPositionString.value(
						channel.elapsedSeconds,
						channel.duration
					)
				)
			}.defer;
		});
		view.layout_(
			HLayout(
				widgets[\channelNameLabel].fixedWidth_(unitSize * 4),
				widgets[\fileNameLabel],
				nil,
				widgets[\playPositionLabel],
				nil,
				widgets[\playButton]
			).margins_(0).spacing_(0)
		);
		view.fixedSize_(Size(unitSize * 20, unitSize));
		^view;
	}

	*buildDropMenuView {
		|
		label, options, callback,
		unitSize, font, color, listenTo,
		model
		|
		var view = View();
		var widgets = ();
		var updater;
		var valueIndex = options.detectIndex({arg it;
			it == model.perform(listenTo ? \value);
		});
		widgets.put(\menu, 
			PopUpMenu()
			.font_(font)
			.background_(color)
			.items_(options)
			.action_({|m|
				callback.value(m.item);
			})
			.value_(valueIndex)
		);
		SimpleController(model).put(listenTo ? \value, {
			{
				widgets[\menu].value_(
					options.detectIndex({|it|
						it == model.perform(listenTo ? \value)
					})
				);
			}.defer;
		});
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
		view.fixedSize_(Size(unitSize * 4, unitSize * 3));

		view.background_(color);
		^view;
	}

	*buildPlayerView{
		|
		label, unitSize, font, color,
		player
		|
		var width = unitSize * 24;
		var height = unitSize * 6;
		var view = View();
		var widgets = ();
		var makeFolderViewString = {
			var result;
			if(player.soundFilesFolder.notNil, {
				result = "% (% MB)".format(
					player.soundFilesFolder,
					player.getSoundFilesFolderSizeInMB
				);
			});
			result;
		};
		var makeBufferCountString = {|count|
			"Num allocated buffers: % ".format(count);
		};
		widgets.put(\volumeView, this.buildKnobView(
			label,
			unitSize, font, color,
			ControlSpec(-90, 6.0, \db, units: \dB), // spec
			player,
			{|val, player, spec| 
				player.volume_(spec.constrain(val));
			},
			\volume
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
						player.soundFilesFolder_(folder);
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
		widgets.put(\bufferCountLabel, 
			StaticText()
			.font_(font)
			.background_(color.blend(Color.white), 0.4)
			.string_(
				makeBufferCountString.value(
					player.numBuffers
				)
			)
		);
		widgets.put(\playerPlayButton, 
			this.buildSmallToggleButton(
				"PLAY", unitSize, font, color,
				player, false, true, true,
				\isPlaying,
				{|val|
					player.play_(val);
				}
			)
		);
		widgets.put(\channelViews, 
			player.channels.collect({|channel|
				this.buildPlayerChannelView(
					channel, unitSize, font, color
				)
			})
		);

		SimpleController(player).put(\soundFilesFolder, {
			{
				widgets[\folderDisplayLabel].string_(
					makeFolderViewString.value
				);
			}.defer;
		});
		SimpleController(player).put(\buffers, {
			{
				widgets[\bufferCountLabel].string_(
					makeBufferCountString.value(
						player.numBuffers;
					)
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
									widgets[\bufferCountLabel],
									nil,
									widgets[\playerPlayButton]
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
		^view;
	}

	*buildTimevarPanel{
		|
		runtime, settings
		|
		var unitSize, color, font;
		var buildTimevarCtrlView;
		unitSize = settings['unitSize'];
		color = settings[\color];
		font = settings[\font];
		buildTimevarCtrlView = {|letter, runtime|
			var v;
			var model = runtime.timevars[letter];
			v = this.buildKnobView(
				letter, unitSize, font, color,
				model.spec,
				model,
				{|val, model, spec|
					runtime.setTimevarValue(letter, val );
				}
			);
			v;
		};
		^HLayout(
			*(runtime.timevars.keys.asArray.sort.collect({|letter|
				var timevar = runtime.timevars[letter];
				buildTimevarCtrlView.value(letter, runtime);
			})) ++ [
				VLayout(
					HLayout(
						this.buildDropMenuView(
							"PRESETS",
							runtime.timevarPresets.keys.asArray.sort,
							{|val| 
								runtime.currentTimevarPreset_(val.asSymbol);
							},
							unitSize, font, color, \currentTimevarPreset,
							runtime
						)
					),
					nil
				),
				nil
			]
		)
	}

	*buildPlayersPanel{|runtime, settings|
		var font, color, unitSize;
		font = settings[\font];
		color = settings[\color];
		unitSize = settings[\unitSize];
		^HLayout(
			this.buildPlayerView(
				'FOSSELYD',
				settings[\unitSize],
				settings[\font], 
				settings[\color],
				runtime.players['fosselyd']
			),
			this.buildPlayerView(
				'MUSIKKLYD',
				settings[\unitSize],
				settings[\font], 
				settings[\color],
				runtime.players['musikklyd']
			),
			VLayout(
				this.buildDropMenuView(
					"BANK",
					runtime.players[\musikklyd].soundBankNames,
					{|val| 
						runtime.players[\musikklyd].soundBankName_(val);
					},
					unitSize, font, color, \currentSoundBankName,
					runtime.players[\musikklyd]
				),
				nil,
				Button()
				.states_([[
					"Go To\nNext Bank",
					Color.black,
					color.copy.alpha_(0.7)
				]])
				.font_(font.copy.size(16))
				.action_({
					runtime.players[\musikklyd].goToNextBank
				}),
			), 
			nil
		)
	}

	*buildButtonPanelView{|runtime, settings|
		var unitSize, color, font;
		var buildTimevarCtrlView;
		var widgets = ();
		var initAlpha;
		unitSize = settings['unitSize'];
		color = settings[\color];
		font = settings[\font];
		if(runtime.isPlaying, {
			initAlpha = 1.0
		}, {
			initAlpha = 0.2
		});
		widgets.put(\modeButton, 
			this.buildLargeToggleButton(
				"MODE", unitSize, font, color,
				runtime, 'absence', 'presence',
				Color.red.alpha_(initAlpha),
				Color.green.alpha_(initAlpha),
				runtime.isPlaying,
				\mode,
				{|val|
					runtime.mode_(val);
				},
				postInitAction: {|v, w|
					var col = color.deepCopy;
					SimpleController(runtime).put(\isPlaying, {
						{
							var buttonStates;
							w[\button].acceptsMouse_(runtime.isPlaying);
							w[\button].canFocus_(runtime.isPlaying);
							buttonStates = w[\button].states.copy;
							if(runtime.isPlaying, {
								buttonStates = [
									buttonStates[0].put(2, Color.red.alpha_(1.0)),
									buttonStates[1].put(2, Color.green.alpha_(1.0)),
								]
							}, {
								buttonStates = [
									buttonStates[0].put(2, Color.red.alpha_(0.2)),
									buttonStates[1].put(2, Color.green.alpha_(0.2)),
								]
							});
							w[\button].states_(buttonStates);
						}.defer;
					});
				}
			)
		);
		^HLayout(
			this.buildLargeToggleButton(
				"PLAY", unitSize, font, color,
				runtime, false, true,
				Color.red, Color.green, true,
				\isPlaying,
				{|val|
					runtime.play_(val);
				}
			),
			widgets[\modeButton],
			this.buildLargeToggleButton(
				"SENSOR", unitSize, font, color,
				runtime, false, true,
				Color.red.alpha_(0.2), Color.green.alpha_(0.2), false,
				\presenceSensor,
				{|val|
					runtime.presenceSensor_(val)
				}
			),
			this.buildLargeToggleButton(
				"MUTE SENSOR", unitSize, font, color,
				runtime, false, true,
				Color.red, Color.green, true,
				\sensorMuted,
				{|val|
					runtime.sensorMuted_(val)
				}
			),
			VLayout(
				nil,
				StaticText()
				.string_("OSC listen port: % ".format(
					NetAddr.localAddr.port
				))
				.font_(font),
				StaticText()
				.string_("soundFileStrategy: %".format(
					runtime.soundFileStrategy
				))
				.font_(font),
			)
		)
	}
}
