Akatsuki
============

[![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-Akatsuki-green.svg?style=flat)](https://android-arsenal.com/details/1/2230)
[![Build Status](https://travis-ci.org/tom91136/Akatsuki.svg)](https://travis-ci.org/tom91136/Akatsuki)


Akatsuki is an Android library that handles [state restoration](http://developer.android.com/training/basics/activity-lifecycle/recreating.html) via annotations.
The library automatically generates source files through JSR269 to ensure almost<sup>1</sup> zero performance impact.

Typical usage looks like:
```java
public class MainActivity extends Activity {

    @Retained String myString;
    @Retained int myInt;
    @Retained Account account; // Account implements Parcelable

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Akatsuki.restore(this, savedInstanceState);
        //everything restored!   
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Akatsuki.save(this, outState);
    }
}
```
Annotate the fields you want to persist, Akatsuki takes care of the rest.

If you are new to Android development, retaining fields are painful and error prone. You have to create string keys and then manually call the `set<Type>` and `get<Type>`of the `Bundle` object.
To demonstrate, here is the same Activity **without using Akatsuki:**

```java
public class MainActivity extends Activity {

    private static final String MY_STRING = "myString";
    private static final String MY_INT = "myInt";
    private static final String ACCOUNT = "account";
	
	String myString;
	int myInt;
    Account account;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		myString = savedInstanceState.getString(MY_STRING);
		myInt = savedInstanceState.getInt(MY_INT);
		account = savedInstanceState.getParcelable(ACCOUNT);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(MY_STRING, myString);
		outState.putInt(MY_INT, myInt);
		outState.putParcelable(ACCOUNT, account);
	}
}
```
<sup>1</sup>Reflection is used only once to locate the generated classes.

----------

Advanced usage
---------
Akatsuki supports any kind of objects(Fragments, Services, and Views), not just Activity. As long as the persisting medium is a `Bundle`, the code generation will work.

For `View` states, Akatsuki provides several utility methods to make view state restoration possible. To demonstrate: 

```java
public static class MyView extends View {

	@Retained int myInt;

	public MyView(Context context) {
		super(context);
	}

	@Override
	protected Parcelable onSaveInstanceState() {
		return Akatsuki.save(this, super.onSaveInstanceState());
	}

	@Override
	protected void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(Akatsuki.restore(this, state));
	}
}
```
<a name="nested-retain"></a>Because `Bundle` is just a type safe container for `Parcel`, you can also use Akatsuki like so:

```java
// you can have some bean
static class MyStuff{
	@Retained int foo;
	@Retained String bar;
	// your getters/setters etc
}

// serialize your bean into a Bundle
MyStuff stuff = new MyStuff();
Bundle bundle = Akatsuki.serialize(stuff);


// restore your bean from the Bundle
MyStuff stuff = Akatsuki.deserialize(new MyStuff(), bundle);
```

For debug purposes, `@Retain` has a method called `skip()`, when set to true, the field will not be retained. Adding the `transient` modifier also has the same effect.



Supported types
---------------
All data types supported by `Bundle` is supported by Akatsuki, that includes:
```java
IBinder
boolean
boolean[]
Bundle
byte
byte[]
char
char[]
CharSequence
CharSequence[]
ArrayList<CharSequence>
double
double[]
float
float[]
IBinder
int
int[]
ArrayList<Integer>
long
long[]
String
Parcelable
Parcelable[]
ArrayList<T>
Serializable
short
short[]
Size
SizeF
SparseArray<T>
String
String[]
ArrayList<String>
```
ETS (Extended Type Support) allows you to retain even more types:

 - Multidimensional array of all supported types (not encouraged though as an unique key will have to be generated for EVERY element)
 - `LinkedList`, `CopyOnWriteArrayList` or simply `List` for all `ArrayList` supported types

More types will be added, submit an issue or pull request if you feel something is missing from the list.

##Annotated types
Types annotated with `@Retained` will also be saved and restored **but not instantiated**.
What this means is that you would have to do something like this:

    
```java
@Retained MyType foo; // MyType has fields annotated with @Retained
@Retained int bar;

@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    // manually instantiate your custom type
	foo = new MyType(/* some arguments*/); 
	// bar is a supported type so no need to instantiate
    Akatsuki.restore(this, savedInstanceState); 
}
```
This is because Akatsuki does not know how your custom type is created so you will have to instantiate it yourself **BEFORE** `Akatsuki.restore(Object, Bundle)` is called. 
##Generic parameters
Generic parameters are supported if and only if the type can be computed at compile time. This means that a type `<T>` must have known bounds.  
The following examples will work
```java
<T extends Parcelable> 
<T extends Serializable>
<T extends Parcelable & Serializable> // intersection type
```
When a intersection type is found, a search for supported type is executed from left to right. If a supported type is found, that type will be used for serialization.

Inheritance
-----------
**Inheritance is fully supported**, annotated fields will continue to persist in subclasses.


##Field hiding
[Field hiding](https://docs.oracle.com/javase/tutorial/java/IandI/hidevariables.html) is supported through traversing the class hierarchy to avoid field name collisions.
NOTE: The use of field hiding is discouraged as it makes code hard to follow.



Parceler support
----------------
<a name="parceler></a>Akatsuki supports [Parceler](https://github.com/johncarl81/parceler) annotated beans
Simply add `@IncludeClasses(ParcelerSupport.class)` to any class in your project.(MainActivity or your custom Application class perhaps).
And don't forget to import:
```groovy
compile 'com.sora.util.akatsuki:akatsuki-parceler:0.0.1@aar'
```


TypeConverters
--------------
Akatsuki supports pluggable `TypeConverter`s , this allows you to support any type by writing a `TypeConverter` for it. 

Here's a custom type:
```java
public class Foo {
	String name;
	int id;
	Date timestamp;
	// more stuff
}
```
The `TypeConverter` would look something like this:
```java
public class FooConverter implements TypeConverter<Foo> {
	@Override
	public void save(Bundle bundle, Foo foo, String key) {
		bundle.putString(key + "name", foo.name);
		bundle.putInt(key + "id", foo.id);
		// Date.toString() is for illustration purpose only, do not use!
		bundle.putString(key + "timestamp", foo.timestamp.toString());
	}
	@Override
	public Foo restore(Bundle bundle, Foo foo, String key) {
		foo.name = bundle.getString(key + "name");
		foo.id = bundle.getInt(key + "id");
		// new Date(String) is for illustration purpose only, do not use!
		foo.timestamp = new Date(bundle.getString(key + "timestamp"));
		return foo;
	}
}
```
The example above can be simpler:
```java
public class BetterFooConverter extends MultiKeyTypeConverter<Foo> {
	@Override
	protected String[] saveMultiple(Bundle bundle, Foo foo) {
		String[] keys = generateKey(3);
		bundle.putString(keys[0], foo.name);
		bundle.putInt(keys[1], foo.id);
		bundle.putString(keys[2], foo.timestamp.toString());
		return keys;
	}
	@Override
	protected Foo restoreMultiple(Bundle bundle, Foo foo, String[] keys) {
		foo.name = bundle.getString(keys[0]);
		foo.id = bundle.getInt(keys[1]);
		foo.timestamp = new Date(bundle.getString(keys[2]));
		return foo;
	}
}
```
To let Akatsuki know about the converter, simply use:

```java
@Retained(converter = BetterFooConverter.class) Foo myFoo;
```
or if you have many fields, register the converter so that `Foo` automatically uses the converter:
```java
@DeclaredConverter(@TypeFilter(type=@TypeConstraint(type = Foo.class)))
public class BetterFooConverter extends MultiKeyTypeConverter<Foo> {
	// ...
}
```
Using converters do incur a tiny performance impact as the converter has to be instantiated before the type can be properly serialized(Only once through reflection). Also, custom types can also have `@Retained` like described [here](#nested-retain).
In that case we can just add `@Retained` to all the fields like:
```java
public class Foo {
	@Retained String name;
	@Retained int id;
	@Retained Date timestamp; // not supported natively...
}
```
And write your custom `TypeConverter` just for the `Date` type. 
It's up to you to decide whether you want a `TypeConverter` or annotate fields with `@Retained`. Normally you would prefer `@Retianed` for codes that you control and `TypeConverter` for other types that you cannot modify(eg: types from another library ).

@TransformationTemplate
----------------------
`@TransformationTemplate` allows you to support arbitrary types. This is the zero performance impact alternative to `TypeConverter`. To understand how this works, here's the what the class `ParcelerSupport` actually looks like:

```java
@TransformationTemplate(
		save = "{{bundle}}.putParcelable(\"{{fieldName}}\", org.parceler.Parcels.wrap({{fieldName}}))",
		restore = "{{fieldName}} = org.parceler.Parcels.unwrap({{bundle}}.getParcelable(\"{{fieldName}}\"))",
		filters = @TypeFilter(type=@TypeConstraint(type = Parcel.class)),
		execution = Execution.BEFORE)
public class ParcelerSupport {
    // dummy class, any class in the project will work
}
```
 [Parceler](https://github.com/johncarl81/parceler) annotated beans need `Parcels.wrap()` and `Parcels.unwrap()` before the object becomes usable. In the example above, we simply create a code template that includes the custom logic for wrapping and unwrapping Parceler objects.
Akatsuki uses [mustache](https://mustache.github.io/) to convert the template into code that gets emitted into the generated source file. Everything is done in compile time so there will be no runtime cost. The `@TransformationTemplate` annotation has a retention of `RetentionPolicy.CLASS` so that it has no effect in runtime while other libraries using the annotation could still retain the template. For more information on how to write Transformation templates, the [javadoc]() contains examples and docs for all methods in the annotation.

Due to a limitation of APT, if the template is located in another library, you have to include the class that is annotated with `@TransformationTemplate`  with `@IncludeClasses(TheClass.class)`, see the [Parceler support section ](#parceler) for more info.

**Warning: `@TransformationTemplate` does not do any validation on the templates, however,  syntax errors does prevent your project from compiling which can save you from debugging heedlessly.**

Opt-out
-------
Akatsuki is designed to be flexible. You can still retain your fields the old way in conjunction with Akatsuki and nothing will break. There is only one thing to look out for: key collisions (Akatsuki uses the field name as key). 


Why another library?
--------
Currently, we have [Icepick](https://github.com/frankiesardo/icepick) and possibly some other libraries that I'm not aware of. The main motivation for this library is due to the inflexibility of Icepick. Icepick does not support [Parceler](https://github.com/johncarl81/parceler) which is a deal breaker for me (there's a [discussion](https://github.com/frankiesardo/icepick/pull/20) on why that's the case). Incompatibility between Parceler and Icepick does not justify the creation of a another library, I could have forked Icepick and added the support myself and be happy. But no, I want to see how APT works so I did a [clean room implementation](https://en.wikipedia.org/wiki/Clean_room_design) of Icepick and added some other features.

Download
--------

```groovy
 compile 'com.sora.util.akatsuki:akatsuki-api:0.0.1'
 apt 'com.sora.util.akatsuki:akatsuki-compiler:0.0.1'
```
Optional parceler support:
```groovy
 compile 'com.sora.util.akatsuki:akatsuki-parceler:0.0.1@aar'
```
You can download the sample app [here](http://jcenter.bintray.com/com/sora/util/akatsuki/sample/0.0.1/) if you want to test it out (nothing surprising though, just a very simple demo with a `Fragment` + `NumberPicker`/`EditText`).


License
-------

    Copyright 2015 WEI CHEN LIN

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.




