-- Intrinsic pixel dimensions of cover_url, captured from the YouTube API's
-- thumbnail metadata at sync time. Lets the Home masonry gallery size each
-- tile from its real aspect ratio without probing the image first (see the FE's
-- README_HOME_VIDEO_GALLERY_LAYOUT.md "Image Size Metadata").
-- Null on posts that predate this migration and on manually-authored ones.
ALTER TABLE posts ADD COLUMN cover_width  INTEGER;
ALTER TABLE posts ADD COLUMN cover_height INTEGER;
