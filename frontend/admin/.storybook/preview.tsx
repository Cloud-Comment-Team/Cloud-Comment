import type { Preview } from '@storybook/react-vite'
import '../src/index.css'

const preview: Preview = {
  globalTypes: {
    theme: {
      description: 'Тема интерфейса',
      defaultValue: 'light',
      toolbar: { icon: 'paintbrush', items: ['light', 'dark'] },
    },
  },
  decorators: [
    (Story, context) => {
      document.documentElement.dataset.theme = context.globals.theme
      return <div style={{ minHeight: '100vh', padding: 24, background: 'var(--bg)' }}><Story /></div>
    },
  ],
  parameters: {
    viewport: {
      options: {
        phone: { name: 'Телефон 390×844', styles: { width: '390px', height: '844px' } },
        tablet: { name: 'Планшет 768×1024', styles: { width: '768px', height: '1024px' } },
        desktop: { name: 'Компьютер 1440×900', styles: { width: '1440px', height: '900px' } },
      },
    },
    backgrounds: { disable: true },
    controls: { expanded: true },
  },
}

export default preview
