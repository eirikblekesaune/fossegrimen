FossegrimenPlayer{
	var <runtime;
	var <soundFilesFolder;   //these should ideally be private
	var <soundFilesPathNames;//but keeping public for
	var soundFilesDurations;//channel objects to access.
	var <channels;
	var bus;
	var group;
	var <channelsGroup;
	var <volumeBus;
	var buffers;
	var buffersRetainCounts;
	var <isPlaying = false;
	var playProcess;

	*new{|runtime, soundFilesFolder, doWhenInitialized|
		^super.new.init(runtime, soundFilesFolder, doWhenInitialized);
	}

	init{|runtime_, soundFilesFolder_, doWhenInitialized|
		channels = List[];
		runtime = runtime_;
		soundFilesDurations = Dictionary.new;
		soundFilesPathNames = List.new;
		fork{
			var cond = Condition.new;
			this.prInitServerSide(cond);
			buffers = Dictionary.new;
			buffersRetainCounts = Dictionary.new;
			this.soundFilesFolder_(soundFilesFolder_);
			this.prInitChannels;
			"[%] initialized: %".format(
				this.class.name,
				channels
			).postln;
			doWhenInitialized.value(this);
		}
	}
	
	prInitServerSide{|cond|
		var server = runtime.server;
		bus = Bus.audio(server, 2);
		group = Group(server, \addToTail);
		volumeBus = Bus.control(server, 1);
		channelsGroup = Group(group, \addToHead);
		server.sync;
		this.volume_(0);
	}

	prInitChannels{
		this.subclassResponsibilityError(thisMethod);
	}

	soundFilesFolder_{|sfFolder|
		if(File.exists(sfFolder), {
			var pathName = PathName(sfFolder);
			if(soundFilesFolder.notNil, {
				soundFilesDurations.clear;
				soundFilesPathNames.clear;
			});
			soundFilesFolder = pathName.fullPath;
			pathName.files.select({|p|
				this.soundFileNamePattern.matchRegexp(
					p.fileName
				);
			}).do({|p|
				var soundFile;
				soundFile = SoundFile.openRead(p.fullPath);
				if(soundFile.isNil, {
					"Could not read soundFile '%'".format(
						p.fullPath
					).warn;
				}, {
					soundFilesDurations.put(
						p.fullPath, soundFile.duration
					);
					soundFilesPathNames.add( p );
					soundFile.close;
				});
			});
			"Loaded soundfiles for '%' from folder: '%'".format(
				this.class.name, pathName.fullPath
			).postln;
			this.changed(\soundFilesFolder);
		}, {
			"[%] sound files folder not found'%'".format(
				this.class.name,
				soundFilesFolder
			).postln;
		});
	}

	allocBufferForSoundFilePathName{|soundFilePathName, action|
		var buffer, path;
		path = soundFilePathName.fullPath;
		if(buffersRetainCounts.includesKey(path), {
			var count = buffersRetainCounts[path];
			buffersRetainCounts[path] = count + 1;
			if(buffers[path].notNil, {//this is assertion, should never happen
				buffer = buffers[path];
			}, {
				"Could not find buffer: '%'".format(path).warn;
			});
		}, {
			switch(runtime.soundFileStrategy, 
				\fromdisk, {
					buffers[path] = Buffer.cueSoundFile(
						server: runtime.server,
						path: path,
						completionMessage: {|b|
							action.value(b);
						}
					)
				},
				\fromRAM, {
					buffers[path] = Buffer.read(
						server: runtime.server,
						path: path,
						action: {|b|
							action.value(b);
						}
					)
				}
			);
			buffersRetainCounts[path] = (buffersRetainCounts[path] ? 0) + 1;
			this.changed(\buffers)
		});
	}

	numBuffers{
		var result = buffersRetainCounts.keys.size;
		if(buffers.size != result, {
			"Something is wrooong with the buffer allocations".warn;
		});
		^result;
	}

	freeBufferForSoundFilePathName{|soundFilePathName, action|
		var buffer, path;
		path = soundFilePathName.fullPath;
		if(buffersRetainCounts.includesKey(path), {
			var count = buffersRetainCounts[path];
			buffer = buffers[path];
			buffersRetainCounts[path] = count - 1;
			this.changed(\buffers);
			if(buffersRetainCounts[path] == 0, {
				buffer.free({
					buffersRetainCounts.removeAt(path);
					buffers.removeAt(path);
					if(runtime.soundFileStrategy == \fromdisk, {
						buffer.close;
					});
					action.value;
				});
			});
		});
	}

	getSoundFilesFolderSizeInMB{
		var result;
		var path = soundFilesFolder;
		if(path.notNil and: {File.exists(path)}, {
			var stdout = "du -m % | awk '{ print $1 }'".format(
				path
			).unixCmdGetStdOut.drop(-1);//drop the line end
			if("^\\d+$".matchRegexp(stdout), {
				result = stdout.asInteger;
			});
		});
		if(result.isNil, {
			"Failed to get the folder size for '%'".format(
				path
			).warn;
		});
		^result;
	}

	getSoundFileDuration{|soundFileName|
		^soundFilesDurations[soundFileName];
	}

	soundFileNamePattern{
		this.subclassResponsibilityError(thisMethod);
	}

	getRandomSoundfilePathName{
		this.subclassResponsibilityError(thisMethod);
	}

	volume_{|db|
		volumeBus.set(db.dbamp.clip(0.0, 1.0));
		this.changed(\volume);
	}

	volume{
		^volumeBus.getSynchronous.ampdb;
	}

	server{
		^runtime.server;
	}

	play_{|val, settings|
		if(isPlaying != val, {
			if(val, {
				this.prStartPlaying(settings);
			}, {
				this.prStopPlaying(settings);
			})
		})
	}

	prStartPlaying{
		"Started playing player: %".format(this.class.name).postln;
		isPlaying = true;
		this.changed(\isPlaying);
	}

	prStopPlaying{
		"Stopped playing player: %".format(this.class.name).postln;
		isPlaying = false;
		this.changed(\isPlaying);
	}

	free{
		channels.do({|channel| channel.free;});
		buffers.do({|buffer| buffer.free;});
	}
}
