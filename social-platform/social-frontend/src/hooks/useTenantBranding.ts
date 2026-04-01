import { useEffect, useState } from 'react';
import api from '../api/client';

interface TenantBranding {
  tenantId: number;
  companyName: string;
  slug: string;
  plan: string;
  primaryColor: string;
  logoUrl: string;
}

const DEFAULT_BRANDING: TenantBranding = {
  tenantId: 1,
  companyName: 'WorkSphere',
  slug: 'default',
  plan: 'enterprise',
  primaryColor: '#4f46e5',
  logoUrl: '',
};

/**
 * Fetches tenant branding on load and applies it:
 * - Generates a full 10-shade color palette from the primary color
 * - Sets CSS custom properties used by Tailwind's primary-* classes
 * - Updates document title with company name
 * - Returns branding data for UI use (logo, name)
 */
export function useTenantBranding() {
  const [branding, setBranding] = useState<TenantBranding>(DEFAULT_BRANDING);

  useEffect(() => {
    api.get('/branding')
      .then(({ data }) => {
        setBranding(data);

        // Generate full palette and apply as CSS variables
        if (data.primaryColor) {
          const palette = generatePalette(data.primaryColor);
          const root = document.documentElement;
          root.style.setProperty('--color-primary-50', palette[50]);
          root.style.setProperty('--color-primary-100', palette[100]);
          root.style.setProperty('--color-primary-200', palette[200]);
          root.style.setProperty('--color-primary-300', palette[300]);
          root.style.setProperty('--color-primary-400', palette[400]);
          root.style.setProperty('--color-primary-500', palette[500]);
          root.style.setProperty('--color-primary-600', palette[600]);
          root.style.setProperty('--color-primary-700', palette[700]);
          root.style.setProperty('--color-primary-800', palette[800]);
          root.style.setProperty('--color-primary-900', palette[900]);
        }

        // Update page title
        if (data.companyName) {
          document.title = data.companyName;
        }
      })
      .catch(() => {});
  }, []);

  return branding;
}

/**
 * Generate a 10-shade palette from a single hex color.
 * Shade 500 is the input color. Lighter shades mix toward white,
 * darker shades mix toward black.
 */
function generatePalette(hex: string): Record<number, string> {
  const { h, s, l } = hexToHSL(hex);

  return {
    50:  hslToHex(h, Math.min(100, s * 0.3), 97),
    100: hslToHex(h, Math.min(100, s * 0.5), 93),
    200: hslToHex(h, Math.min(100, s * 0.7), 86),
    300: hslToHex(h, Math.min(100, s * 0.85), 76),
    400: hslToHex(h, Math.min(100, s * 0.95), 64),
    500: hslToHex(h, s, l),                        // Input color
    600: hslToHex(h, Math.min(100, s * 1.05), l * 0.85),
    700: hslToHex(h, Math.min(100, s * 1.1), l * 0.72),
    800: hslToHex(h, Math.min(100, s * 1.1), l * 0.58),
    900: hslToHex(h, Math.min(100, s * 1.05), l * 0.45),
  };
}

function hexToHSL(hex: string): { h: number; s: number; l: number } {
  hex = hex.replace('#', '');
  const r = parseInt(hex.substr(0, 2), 16) / 255;
  const g = parseInt(hex.substr(2, 2), 16) / 255;
  const b = parseInt(hex.substr(4, 2), 16) / 255;

  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let h = 0, s = 0;
  const l = (max + min) / 2;

  if (max !== min) {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    if (max === r) h = ((g - b) / d + (g < b ? 6 : 0)) / 6;
    else if (max === g) h = ((b - r) / d + 2) / 6;
    else h = ((r - g) / d + 4) / 6;
  }

  return { h: h * 360, s: s * 100, l: l * 100 };
}

function hslToHex(h: number, s: number, l: number): string {
  s /= 100;
  l /= 100;
  const a = s * Math.min(l, 1 - l);
  const f = (n: number) => {
    const k = (n + h / 30) % 12;
    const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
    return Math.round(255 * color).toString(16).padStart(2, '0');
  };
  return `#${f(0)}${f(8)}${f(4)}`;
}
