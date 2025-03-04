File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()

// nisse keys
assert buildLog.text.contains ('nisse.os.name')
assert buildLog.text.contains ('nisse.os.arch')
assert buildLog.text.contains ('nisse.os.bitness')
assert buildLog.text.contains ('nisse.os.version')
assert buildLog.text.contains ('nisse.os.version.major')
assert buildLog.text.contains ('nisse.os.version.minor')
assert buildLog.text.contains ('nisse.os.classifier')
assert buildLog.text.contains ('nisse.os.release')
assert buildLog.text.contains ('nisse.os.release.version')

// os-detectot keys
assert buildLog.text.contains ('os.detected.name')
assert buildLog.text.contains ('os.detected.arch')
assert buildLog.text.contains ('os.detected.bitness')
assert buildLog.text.contains ('os.detected.version')
assert buildLog.text.contains ('os.detected.version.major')
assert buildLog.text.contains ('os.detected.version.minor')
assert buildLog.text.contains ('os.detected.classifier')
assert buildLog.text.contains ('os.detected.release')
assert buildLog.text.contains ('os.detected.release.version')

// alt keys (from translate properties)
assert buildLog.text.contains ('something.else.name')
