File buildLog = new File( basedir, 'build.log' )
assert buildLog.exists()

// Scope exception
assert !buildLog.text.contains('com.google.inject.OutOfScopeException: Cannot access session scope outside of a scoping block')
