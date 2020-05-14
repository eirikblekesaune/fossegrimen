(
	var runtime;
	var projectRootFolder = "..".resolveRelative;
	runtime = FossegrimenRuntime(projectRootFolder, {|runtime|
		{
			var window;
			"Fossegrimen Initialized".postln;
			window = runtime.makeView;
			window.front;
		}.defer;
	});
)
