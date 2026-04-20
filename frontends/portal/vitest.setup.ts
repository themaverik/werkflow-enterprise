import '@testing-library/jest-dom'

// Radix UI requires these DOM APIs not implemented in jsdom
window.HTMLElement.prototype.hasPointerCapture = () => false
window.HTMLElement.prototype.scrollIntoView = () => {}
