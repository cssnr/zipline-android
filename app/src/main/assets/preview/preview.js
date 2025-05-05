const preEl = document.querySelector('pre')
const darkStyle = document.getElementById('code-dark')
const lightStyle = document.getElementById('code-light')

document.addEventListener('DOMContentLoaded', () => {
    console.log('DOMContentLoaded')
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
    applyTheme(mediaQuery)
    mediaQuery.addEventListener('change', applyTheme)

    if (typeof Android !== 'undefined') {
        console.log('Android')
        Android.notifyReady()
    }
})

function applyTheme(mediaQuery) {
    console.log(`applyTheme: matches: ${mediaQuery.matches}`)
    if (mediaQuery.matches) {
        darkStyle.disabled = false
        lightStyle.disabled = true
    } else {
        darkStyle.disabled = true
        lightStyle.disabled = false
    }
}

function addContent(content) {
    preEl.textContent = content
    hljs.highlightElement(preEl)
}
