$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:PAYMENT_BACKEND_URL = 'https://swiftpay-merchant.onrender.com/'
Set-Location 'C:\Users\densh\Downloads\swiftpay-merchant-main\swiftpay-merchant-main'
.\gradlew.bat :app:assembleRelease --no-daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8'
