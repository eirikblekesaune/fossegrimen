# Fossegrimen

SuperCollider system by Eirik Blekesaune (@eirikblekesaune) for installation _Fossegrimen_ by Jørn Egseth.

# Installation
This system is not dependant on any external libraries/Quarks and it should be only a matter of running the 'bin/Fossegrim' script.
This is developed on SuperCollider 3.11.0-4 on Arch Linux [https://www.archlinux.org/packages/community/x86_64/supercollider/]

When installing on a new computer you have to set the path to the lib directory in the `data/sclang_conf.yaml` file.
This makes sclang start with only the 'lib' folder as class external library, ensuring that no other SuperCollider-class files will be compiled in the sclang session.

# Startup
The startup script in 'bin' only starts sclang but specifying the runtime directory to the root of this project.
