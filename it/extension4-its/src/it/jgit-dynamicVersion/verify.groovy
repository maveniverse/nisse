File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('nisse.jgit.dynamicVersion=0.0.1')
