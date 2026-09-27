const DEFAULT_COVER_ASPECT_RATIO = 16 / 9;

export const parseCoverAspectRatio = (ratio?: string) => {
    if (!ratio) {
        return DEFAULT_COVER_ASPECT_RATIO;
    }
    const [width, height] = ratio.split(":").map((item) => Number(item));
    if (!width || !height || width <= 0 || height <= 0) {
        return DEFAULT_COVER_ASPECT_RATIO;
    }
    return width / height;
};
