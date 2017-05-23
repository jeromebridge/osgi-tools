osgi-tools
==========

OSGi Bundles that help diagnose issues on OSGi containers. Testing a change.

[ ![Codeship Status for jeromebridge/osgi-tools](https://codeship.com/projects/ac3a1b00-4ab2-0132-efcb-7aa9472b8ea5/status)](https://codeship.com/projects/46411)


#### Encrypting Files For Rultor
<a href="http://www.yegor256.com/2014/08/19/how-to-release-to-maven-central.html">How To Release To Maven Central</a>
Generate the keys. Accept the defaults and enter the required inputs.  Leave the passphrase empty.
````
gpg --gen-key

````

Copy the generated keys.
````
cp ~/.gnupg/pubring.gpg .; cp ~/.gnupg/secring.gpg .
````

Make sure you include the gpg properties in your `settings.xml`
```xml
<settings>
  <profiles>
    <profile>
      <id>ci</id>
	  <activation>
		<activeByDefault>true</activeByDefault>
	  </activation>
      <properties>
        <gpg.homedir>/home/r</gpg.homedir>
        <gpg.keyname>924CF0D9</gpg.keyname>
        <gpg.passphrase></gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

Encrypt files.
```shell
rultor encrypt -p jeromebridge/osgi-tools pubring.gpg
rultor encrypt -p jeromebridge/osgi-tools secring.gpg
rultor encrypt -p jeromebridge/osgi-tools settings.xml
```

Local Build
```shell
VERSION=1.00.004
mvn versions:set "-DnewVersion=$VERSION"
find . -name "pom.xml" -exec mvn versions:set "-DnewVersion=${VERSION}" -f {} \;
mvn clean install
```