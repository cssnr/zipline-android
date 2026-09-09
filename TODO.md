# TODO

## Bottom Sheet Snackbar

The bottom sheet snackbar uses its own Snackbar handler.

- [FilesBottomSheet.kt](app/src/main/java/org/cssnr/zipline/ui/files/FilesBottomSheet.kt)

## Preview Download Requires Auth

`DownloadManager.Request` has no API for custom headers/cookies, so the
"Download" action in the file preview (and the existing `downloadAll` path in
`FilesFragment.kt`) sends no auth and will fail (401) on token-protected
servers. Fixing this requires reimplementing the download (e.g. OkHttp stream
to a file) instead of `DownloadManager`.

## Open URL Uses text/plain Chooser

The shared `Context.openUrl` helper (`FilesFragment.kt`) opens a URL with
`setDataAndType(url, "text/plain")`. A browser intent (`ACTION_VIEW`) would be
more natural. Changing it also affects `FilesBottomSheet.kt`.

## Thumbnails

Zipline UI currently says:

```text
Enables thumbnail generation for images. Requires a server restart.
```

But this statement is false and only works for videos.

If this ever gets implemented, the following PR should be added to this codebase:

- <https://github.com/django-files/android-client/pull/89>
