# Data model

## Users and roles

CloudComment stores application users in `app_users`.

`app_users` is compatible with upcoming registration, login and authorization tasks:

- `id` is a generated UUID primary key.
- `email` is unique and sized for RFC-compatible email addresses.
- `password_hash` stores a password hash, not a raw password.
- `display_name` is optional.
- `is_enabled` can disable access without deleting the account.
- `created_at` and `updated_at` support audit and profile-management flows.

Roles are stored in the `roles` reference table and assigned through `user_roles`.

MVP roles:

- `OWNER` - site owner with administrative access to owned resources.
- `COMMENTER` - authenticated visitor who can publish comments.
- `MODERATOR` - reserved for future moderation flows.

`user_roles` keeps a many-to-many relationship between users and roles. This allows later authorization work to support users with multiple permissions without changing the core user table.
