Pod::Spec.new do |s|
  s.name = 'ModelsHub'
  s.version = '0.0.1'
  s.summary = 'ModelsHub Capacitor Plugin'
  s.license = 'MIT'
  s.homepage = '...'
  s.author = '...'
  s.source = { :git => '...', :tag => s.version.to_s }
  s.source_files = 'Plugin/**/*.{swift,h,m,mm}'
  s.dependency 'Capacitor'
  s.dependency 'SSZipArchive'
  s.ios.deployment_target = '13.0'
end
