import { FastColor } from "@ant-design/fast-color";

// Keep the chosen brand hue while giving each surface and its foreground a role.
// These are ZrLog's Web role mappings, not an HCT / dynamic-color implementation.
export const materialColors = (seed: string, dark: boolean) => {
    const brand = new FastColor(seed);
    // Mix surfaces only with achromatic grays; their tint must follow the saved seed.
    const surface = brand.mix(dark ? "#111111" : "#fcfcfc", 97);
    const onSurface = brand.mix(dark ? "#e6e6e6" : "#1c1c1c", 94);
    const primaryContainer = dark ? brand.shade(70) : brand.tint(85);
    const containerHighest = brand.mix(dark ? "#333333" : "#e8e8e8", 96);
    let primary = dark ? brand.tint(68) : brand.clone();
    const onPrimary = dark ? brand.shade(85) : new FastColor("#ffffff");
    // Check both filled labels and primary-colored links on tonal surfaces.
    while (
        [
            [primary, primaryContainer],
            [primary, containerHighest],
            [primary, onPrimary],
            [primary.mix(onPrimary, 12), onPrimary],
        ].some(
            ([foreground, background]) =>
                (Math.max(foreground.getLuminance(), background.getLuminance()) + 0.05) /
                    (Math.min(foreground.getLuminance(), background.getLuminance()) + 0.05) <
                4.5
        )
    ) {
        primary = dark ? primary.tint(5) : primary.shade(5);
    }
    return {
        primary: primary.toHexString(),
        primaryHover: primary.mix(onPrimary, 8).toHexString(),
        primaryActive: primary.mix(onPrimary, 12).toHexString(),
        onPrimary: onPrimary.toHexString(),
        primaryContainer: primaryContainer.toHexString(),
        onPrimaryContainer: (dark ? brand.tint(90) : brand.shade(80)).toHexString(),
        surface: surface.toHexString(),
        container: brand.mix(dark ? "#1c1c1c" : "#ffffff", 98).toHexString(),
        containerHigh: brand.mix(dark ? "#282828" : "#f1f1f1", 96).toHexString(),
        containerHighest: containerHighest.toHexString(),
        onSurface: onSurface.toHexString(),
        onSurfaceVariant: brand.mix(dark ? "#c5c5c5" : "#494949", 94).toHexString(),
        outline: brand.mix(dark ? "#949494" : "#777777", 95).toHexString(),
        outlineVariant: brand.mix(dark ? "#474747" : "#cccccc", 96).toHexString(),
        hover: primary.mix(surface, 92).toHexString(),
        pressed: primary.mix(surface, 88).toHexString(),
    };
};
