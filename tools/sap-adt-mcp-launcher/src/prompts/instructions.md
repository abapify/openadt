# OpenADT ABAP workflow guide

ALWAYS start by calling `abap_list_destinations` and pass an exact destination id
(e.g. ABC_000_USER_EN) in EVERY subsequent tool call.

Create an ABAP object:

1. abap_creation-get_all_creatable_objects (what can be created here)
2. abap_creation-get_object_type_details (what input the chosen type needs)
3. abap_creation-run_validation (ALWAYS before create; fix errors and repeat)
4. abap_transport-get (ALWAYS before create — never call abap_transport-create directly)
5. abap_creation-create_object
6. abap_activate_objects (activate before running tests)

Generate a RAP service:

1. abap_generators-list_generators (read each 'description' to pick the right one)
2. abap_generators-get_schema (ask the user for a package name first)
3. abap_generators-generate_objects

Expose / inspect an OData service:

1. abap_business_services-fetch_services (V4: if isPublished=false, STOP and ask the user to publish the binding)
2. wait for the user to pick a specific service + version
3. abap_business_services-fetch_service_information

Run ABAP Unit Tests:

- `abap_run_unit_tests` expects URIs in VS Code ABAP virtual filesystem format (see docs/abap-uri-format.md)
- Format: `abap://project/repotree-v1/{destination}/{path-to-file}.clas.abap`
- Example: `abap://project/repotree-v1/S0D_200_PPLENKOV_EN/System%20Library/Package/Class/class.clas.abap`
- URL-encode paths with spaces as `%20`
- File does NOT need to be open in VS Code — SAP ADT extension loads it from backend
- Returns "No tests found" (OK) or test execution results

Quality: run `abap_run_unit_tests` after changes; `abap_activate_objects` before testing.
