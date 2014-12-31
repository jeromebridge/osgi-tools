## How do I get access to the BundleContext of my current bundle if I don't have it passed to me?

````
FrameworkUtil.getBundle( getClass() ).getBundleContext();
````

