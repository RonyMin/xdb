# Introduction #

csshX is a tool to control multiple ssh sessions with the same commands: <br /> http://www.gaumire.com/2011/04/26/csshx-for-multiple-parallel-ssh-connections/

# Installing on Mac #
The best way to install it on Mac is:

Install the package manager "Homebrew" for Mac using http://brew.sh/ <br />
Install csshX using brew using "brew install csshx" (and follow the suggestions) <br />
Set "/opt/local/bin/bash" as standard shell in the Terminal.app (Type  "/opt/local/bin/bash" into the current shell -> go to "Shell -> Use as standard", Close and Open Terminal) <br />

Now everything should be ready

# Example #
csshX --ssh\_args="-i /Users/cbinnig/Workspace/XDB/doomdb.pem" -l ec2-user 54.185.38.252 54.188.69.251

Important is that you have to use an absoluta path to the pem file and the pem file should only be accessible for your user (i.e., execute "chmod 600 /Users/cbinnig/Workspace/XDB/doomdb.pem").

After executing the example cmd above as many terminals open as hosts specified!

# Reading host ip addresses from a file #
You can also create a file called /etc/clusters with the following format:

clusterA host1 host2 host3 ... <br />
clusterB host1 host2

e.g. one line could be: <br />
awsDoomDB1 54.185.38.252 54.188.69.251

Then you can call: <br />
csshX --ssh\_args="-i /Users/cbinnig/Workspace/XDB/doomdb.pem" -l ec2-user awsDoomDB1