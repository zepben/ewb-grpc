[//]: # (
Line length on this is intentionally set to 100 unless in code blocks so that the source is easily
readable, even in side by side mode on a laptop screen. If you NEED to go past 100 in a code block
to make the code clear, go for it, just know I'll judge you. 
- Max.
)

# Lessons #

... about protobuf, and this repository.

If you learn something that seems not obvious, or had to reach out for help, it belongs
in here. Edit this file aggressively, often and unapologetically ... even this section ...

... but most of all - accurately.

Use direct code examples wherever you feel you're being slightly generic in your explanation.

### Directory structure ###

A very top level overview intended to give you a bump steer towards where you're meant to be
based on the type of file you're working on. You'll usually start by editing/creating `.yaml`
files in the `spec` directory, then move onto actually implementing what you've described in
`proto`. Assuming you got all that right - (now is a great time to check with someone who's
done this before) - you then probably want to edit the `python`, `java`, and `c` directory's.

  ├─ spec: builds docs for the website  
  │    ├─ ewb: our implemented spec see [spec files](#spec-files-)  
  │    └─ TC57CIM: CIM100 spec  
  ├─ proto: [proto files](#proto-files-) that generate our protobuf classes  
  ├─ c:  
  ├─ java:  
  └─ python:  

### [ZBEX] ###
  - If a class is [ZBEX] all attrs are to be annotated with [ZBEX] also, the reader 
    should immediately know they are dealing with a zepben attr or class
  - Any attr or class that deviates from CIM is [ZBEX]

### spec files ###

  If you are adding any folders, they should be named in PascalCase.
  
  - TC57CIM
    - The base CIM100 model
    - Gets updated when we rebase CIM onto a new standard
    - If you're editing these, you're probably doing the wrong thing
    - If we're adding something and its already in TC57CIM, copy it to `spec files` and change it
      there

  - ewb
    - Defines the CIM objects used in EWB. 
    - More than best effort here to adhere to CIM standards wherever possible instead of just
      defining our own classes at will. We do that, but there's always a very good reason, and a
      very long discussion beforehand, and even then it's done as close to what already exists in
      CIM as possible.
    - Style guide: (not exhaustive)
      - Enums:
        - attributes: 
          - When specifying the attribute name, assume the reader expects the enum name to prepend
            the value:
          
            ```yaml
            name: ContactMethodType
            description: \[ZBEX\] The method to use to make contact with a person or company.
            attributes:
            - name: UNKNOWN
              description: \[ZBEX\] Unknown contact method type.
            - name: EMAIL
              description: \[ZBEX\] Contact via email using the primary address.
            - name: PHONE
              description: \[ZBEX\] Contact by call using the primary phone number.
            - name: MAIL
              description: \[ZBEX\] Letter by post to the contact address is the method of contact.
            ```
        [//]: # (TODO: needs more info, also whats here is probably inaccurate)
    - attributes:
      - name: `nameOfTheAttributeInSnakeCase`.
      - type: `String`, `Boolean`, `MyCustomType List`. regardless of if it's optional, don't put 
        `AttrType?` here, you're not at that point in your change yet. Only put lists here if the
        order in the list matters, otherwise it should be an association.
      - description: `[ZBEX] ` annotation prepends the description if this is a CIM extension.
    - associations:
      - `source` - Usually the class your defining in this `spec`.
      - `target` - The class we are associating with.
      - `sourceDescription`: `[ZBEX] ` annotation prepends the description if source or target is a
        CIM extension.
      - `targetDescription`: `[ZBEX] ` annotation prepends the description if source or target is a
        CIM extension.
        
### proto files ###

  If you are adding any folders, they should be named in lowercase.
 
  - Optionals:
    - `oneof`: Expected to be one of `n` options:
      ``` protobuf
      oneof forwardLDCBlocking {
          google.protobuf.NullValue forwardLDCBlockingNull = 9;
          bool forwardLDCBlockingSet = 10;
      }
      ```
      
      The message will contain only one of the fields inside the `{ ... }`.

      Example code in python handling this.
      ```python
      forward_ldc_blocking = None if pb.HasField("forwardLDCBlockingNull") else pb.forwardLDCBlockingSet
      ```
    
    - What do the numbers after an attribute name mean?  
      They're the field number, if you delete one, *DO NOT* change the field number of the rest of
      the attributes to make it all sequential again, there's big impact reasons. mark the name,
      and the field number as reserved:
      
      ```protobuf
      enum Foo {
          reserved 2, 15, 9 to 11, 40 to max;
          reserved "FOO", "BAR";
      }
      ```

  - Enums:
    
    - Naming:
      ``` protobuf
      enum ContactMethodType {
          CONTACT_METHOD_TYPE_UNKNOWN 0;  // default
          CONTACT_METHOD_TYPE_EMAIL = 1;  // specified enum values
      }
      ```
      - Default value must be field 0. Should also end in `[ENUM_NAME]_UNSPECIFIED` or 
        `[ENUM_NAME]_UNKNOWN`.
      - Enum name should be `PascalCase`, enum values should be `UPPER_CASE_WITH_UNDERSCORES` and
        start with the enum name.

> If any part of this was useful - remember that - and don't forget to add what you learn.
