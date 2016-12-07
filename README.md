osgi-tools
==========

OSGi Bundles that help diagnose issues on OSGi containers.

[ ![Codeship Status for jeromebridge/osgi-tools](https://codeship.com/projects/ac3a1b00-4ab2-0132-efcb-7aa9472b8ea5/status)](https://codeship.com/projects/46411)

Generate the keys. Accept the defaults and enter the required inputs.  You may also use a passphrase.
````
gpg --gen-key

````

Copy the generated keys.
````
cp ~/.gnupg/pubring.gpg .; cp ~/.gnupg/secring.gpg .
````
