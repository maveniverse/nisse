File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()
assert buildLog.text.contains ('os.detected.name')
assert buildLog.text.contains ('os.detected.arch')
assert buildLog.text.contains ('os.detected.bitness')
assert buildLog.text.contains ('os.detected.classifier')

// alt keys
assert buildLog.text.contains ('something.else.name')
assert buildLog.text.contains ('another.arch')

// falback applied as well to some
assert buildLog.text.contains ('nisse.os.bitness')

// removed
assert !buildLog.text.contains ('nisse.os.version.minor')
