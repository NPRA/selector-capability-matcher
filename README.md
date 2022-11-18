# README #

The idea is that we define a simple contract of “boolean match(String selector, String capabilityJson)” which allows us to write very plain unit test cases.

Criteria would be:

* evaluates in “true” if a message in the capability stream could match the selector given that the capability properties are mandatory application properties and can only have values that are described in the capability;
* quadTree gets the “special treatment”;
* properties that are not defined in the capability and are referenced in the selector shouldn’t result in a “false”.

## Design notes

2022-02-07 -- The main goal of this is to answer the question "does this capability match this selector?". This question can be answered "true" if and only if it is _possible_ to make a message, which matches the selector, from the capability.   
The standard Expression code from Qpid cannot help with that for complicated cases, because (for example) `unknownField = 'Jacques'` and `unknownField <> 'Jacques'` should both result in "capability matches selector", but since "not equals" is implemented as "not(equals)", there's no obvious way of doing this without rewriting all the expression logic.  
So, if we're going to rewrite it _anyway_, we may as well see if we can make this work for several cases at once, by using [Three valued logic](https://en.wikipedia.org/wiki/Three-valued_logic). This would work by using `true` and `false` as usual, but adding `unknown`, and adapting the logic to support it.  
Example evaluations:
```
true OR unknown -> true
false OR unknown -> unknown
true AND unknown -> unknown
false AND unknown -> false
NOT unknown -> unknown
```
And for the purposes of answering "is it possible for this capability to make a message which matches this selector?", `unknown` effectively means "well, I suppose it _could_...", which we can treat as a match.  
## TODO - future

### Licenses

A part of this code is relying on classes and snippets lifted from the Apache Qpid project. There might be unresolved licensing issues.