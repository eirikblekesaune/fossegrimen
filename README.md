# Fossegrimen

SuperCollider system by Eirik Blekesaune (@eirikblekesaune) for installation _Fossegrimen_ by Jørn Egseth.

# Installation
This system is not dependant on any external libraries/Quarks and it should be only a matter of running the 'bin/Fossegrim' script.
This is developed on SuperCollider 3.11.0-4 on Arch Linux [https://www.archlinux.org/packages/community/x86_64/supercollider/]

When running this for the first time on a computer, the `bin/Fossegrimen` script will write a config file at `data/sclang_conf.yaml`. 
This makes sclang start with only the 'lib' folder as class external library, ensuring that no other SuperCollider-class files will be compiled in the sclang session.
If SuperCollider complains about not finding a Class, especially if it is named something with 'Fossegrimen' in it, failing to generate this `sclang_conf.yaml`-file is a likely culprit.

# Startup
The startup script in 'bin' only starts sclang but specifying the runtime directory to the root of this project.
