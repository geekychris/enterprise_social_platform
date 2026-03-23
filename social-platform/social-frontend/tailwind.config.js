/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eff6ff',
          100: '#dbeafe',
          200: '#bfdbfe',
          300: '#93c5fd',
          400: '#60a5fa',
          500: '#1877F2',
          600: '#1565d8',
          700: '#1254b5',
          800: '#0f4392',
          900: '#0c326f',
        },
        gray: {
          50: '#f0f2f5',
          100: '#e4e6eb',
          200: '#d8dadf',
          300: '#bec3c9',
          400: '#8a8d91',
          500: '#65676b',
          600: '#4e4f50',
          700: '#3a3b3c',
          800: '#242526',
          900: '#18191a',
        },
      },
    },
  },
  plugins: [],
};
