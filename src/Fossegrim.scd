(
	var thisFolderPath = PathName("".resolveRelative);
	var dataPathName = PathName("../data".resolveRelative);
	var configPathName = dataPathName +/+ "Fossegrimen.conf.yaml";
	"Fossgrim running from path: %".format(thisFolderPath.asAbsolutePath).postln;
	"sclang running from: %".format("pwd".unixCmdGetStdOut).postln;
	"Data path: %".format(dataPathName).postln;
	//Function for loading other files with added env vars
	~loadFile = {arg path, envir;
		var func;
		try{
			if(File.exists(path), {
				func = thisProcess.interpreter.compileFile(
					path.asAbsolutePath
				);
				if(func.isNil, {
					Error("loadFile could not compile file '%' !".format(
						path
					)).throw;
				});
				(envir ? ()).use{func.value};
			}, {
				Error("loadFile file '%' not found!".format(path)).throw;
			});
		} {|err|
			"Loading file '%' failed".format(path).postln;
			err.postln;
			err.throw;
		}
	};
	//This runtime environment is where everything related to this installation
	//happens.
	//When passed into the FossegrimInitscript it will be cleared and initialized
	~fossegrimen = Environment.new;
	
	//Load the config data from the data folder
	if(File.exists(configPathName.fullPath), {
		var config = configPathName.fullPath.parseYAMLFile;

		"Loaded config from '%'".format(configPathName.fullPath).postln;
		~fossegrimen.put(\config, config);
	}, {
		"Config file 'Fossegrimen.conf' not found. Using default settings.".postln;
		~fossegrimen.put(\config, Dictionary[
			"fosselyderFolder" -> "sounds/fosselyder".resolveRelative,
			"musikklyderFolder" -> "sounds/musikklyder".resolveRelative
		]);
	});
	fork{
		~loadFile.value(
			"%FossegrimServerStart.scd".format(thisFolderPath.fullPath),
			(
				env: ~fossegrimen,
				serverOptions: (

				)
			)
		);
		//Check if the server did really boot
		if(~fossegrimen.includesKey(\server).not, {
			"Server failed to start. Quitting early".warn;
			~fossegrimen.postln;
			0.exit;
		});
		~loadFile.value(
			"%FossegrimInit.scd".format(thisFolderPath.fullPath),
			(
				env: ~fossegrimen,
				startPlayAfterInit: false,
				fosselyderFolder: "~/sounds/fossegrimen/fosselyder".standardizePath,
				musikklyderFolder: "~/sounds/fossegrimen/musikklyder".standardizePath
			)
		);
		~useGui = true;
		if(~useGui, {
			{
				~loadFile.value(
					"%FossegrimGui.scd".format(thisFolderPath.fullPath),
					(
						env: ~fossegrimen,
						shouldExitOnClose: true
					)
				);
			}.defer;
		})
	}
)
