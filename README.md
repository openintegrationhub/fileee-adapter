# Fileee Openintegrationhub adapter
---

With fileee one can import documents using either the app or specific import functions from within fileee.
Importing documents with services such as Dropbox, GoogleDrive and WebDav is also supported.

Important information like document-type, sender or invoice amount is then automatically extracted by fileee.
Using tags, an intelligent sorting and full text search documents remain easily accessible. Exporting via pdf is also possible.
Using extracted information fileee also provides intelligent reminders for appointments and invoices.
Documents can also be shared easily to one or more users with fileeeSpaces.
Every document is separately encrypted, is only accessible to the user and stored in a german data center.

This is an adapter that enables third party services to connect to fileee in a standardized way.
It supports a set of triggers (`getContacts`) and a set of actions (`upsertContact`, `deleteContact`, etc).

## How to use this adapter
---

In order to use, one has to be a registered Fileee user. Signup here on the fileee [homepage](https://my.fileee.com/signup/). 

Also in order to use the oauth workflow the adapter has to be configured with a OAuth client id stored in the `FILEEE_CLIENT_KEY` environment variable and the corresponding client secred stored in `FILEEE_CLIENT_SECRET`.

After completing registration you can authorize this component using the OAuth workflow provided by the platform.

## Actions and triggers
---

The adapter supports the following actions and triggers:

### Triggers

- Get contacts - polling (`getContacts`)

### Actions

- Delete contact (`deleteContact`)
- Lookup contact (`lookupContact`)
- Upsert contact (`upsertContact`)


**Get contacts**

Get contacts trigger (`getContacts`) performs a request which fetches all new and updated [contacts](https://github.com/openintegrationhub/fileee-adapter/blob/master/schemas/contact.json) from the users fileee account.

**Delete contact**

Delete contact action (`deleteContact`) deletes a contact from your fileee contacts. This actions requires a string parameter `id` of the contact to delete and returns nothing if successful.
> NOTE: Fileee does not really delete the contact. A file `deleted` will be set, which indicates this object is deleted and thus hides this contact.

**Lookup contact**

Lookup contact action (`lookupContact`) fetches a single [contact](https://github.com/openintegrationhub/fileee-adapter/blob/master/schemas/contact.json) from your fileee contacts. This action requires a string parameter `id` of the contact to fetch and returns a [contact](https://github.com/openintegrationhub/fileee-adapter/blob/master/schemas/contact.json).

**Upsert contact**

Upsert contact action (`upsertContact`) updates a [contact](https://github.com/openintegrationhub/fileee-adapter/blob/master/schemas/contact.json) or, if it does not exist, creates a new one. This action takes a fileee [contact](https://github.com/openintegrationhub/fileee-adapter/blob/master/schemas/contact.json) and returns the updated [contact](https://github.com/openintegrationhub/fileee-adapter/blob/master/schemas/contact.json).

## License

Apache-2.0 © [Fileee GmbH](https://www.fileee.com/)