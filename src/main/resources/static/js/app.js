// Se la funzione esposta da Deno esiste, carica dinamicamente la patch crittografica
if (window.saveKeyToDisk) {
    const script = document.createElement('script');
    script.src = '/js/deno-patch.js';
    script.async = false;
    document.head.appendChild(script);
}

/*
async function goToServiceB() {
    // La chiamata fetch invierà automaticamente il cookie "JWT" al server B
    const response = await fetch('https://b.t9s-vault.ct/test', {
        method: 'GET'
    });

    if (response.ok) {
        window.location.href = 'https://b.t9s-vault.ct/test';
    } else {
        // Se il token è scaduto o manca, il server B risponderà 401
        window.location.href = 'https://v.t9s-vault.ct/';
    }
}
 */

async function requestDeviceRegistration() {
    const metadata = await getDeviceMetadata();
    const queryParams = new URLSearchParams(metadata).toString();

    const response = await fetch(`/auth/register?${queryParams}`, { method: 'GET' });
    const options = await response.json();

    const credential = await navigator.credentials.create({ publicKey: decodeOptions(options) });

    const credentialData = {
        tempId: options.tempId,
        credential: {
            id: credential.id,
            rawId: base64UrlEncode(credential.rawId),
            type: credential.type,
            response: {
                attestationObject: base64UrlEncode(credential.response.attestationObject),
                clientDataJSON: base64UrlEncode(credential.response.clientDataJSON)
            }
        }
    };

    const finishResponse = await fetch('/api/webauthn/register-finish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(credentialData)
    });

    if (finishResponse.status === 503) {
        alert("⚠️ Registrazioni chiuse: il sistema è temporaneamente in modalità bloccata.");
        return;
    } else if (!finishResponse.ok) {
        alert("❌ Errore durante la finalizzazione della registrazione: " + finishResponse.statusText);
        return;
    }

    alert("✅ Registrazione completata con successo!");
}

async function getDeviceMetadata() {
    const browserResult = bowser.parse(window.navigator.userAgent);
    return {
        attachment: document.getElementById('attachment').value,
        uv: document.getElementById('uv').value,
        resident: document.getElementById('resident').value,
        browser: browserResult.browser.name,
        os: browserResult.os.name,
        deviceName: document.getElementById('deviceName').value
    };
}

// Funzione per convertire Base64URL in ArrayBuffer
function bufferDecode(value) {
    return Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
}

// Funzione che "allinea" le opzioni ricevute dal server
function decodeOptions(options) {
    options.challenge = bufferDecode(options.challenge);
    options.user.id = bufferDecode(options.user.id);

    if (options.pubKeyCredParams) {
        options.pubKeyCredParams.forEach(param => {
            // Logica specifica se necessario
        });
    }
    return options;
}

// Utility per convertire in modo sicuro qualsiasi ArrayBuffer in Base64URL
function base64UrlEncode(arrayBuffer) {
    const bytes = new Uint8Array(arrayBuffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary)
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=/g, '');
}

async function login() {
    const resp = await fetch('/auth/get-challenge');
    if (!resp.ok) {
        alert("Errore durante il recupero della sfida");
        return;
    }
    const { challenge, loginSessionId } = await resp.json();

    const assertion = await navigator.credentials.get({
        publicKey: {
            challenge: bufferDecode(challenge),
            userVerification: "preferred"
        }
    });

    const jwtResponse = await fetch('/auth/verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            credentialId: base64UrlEncode(assertion.rawId),
            loginSessionId: loginSessionId,
            signature: base64UrlEncode(assertion.response.signature),
            authenticatorData: base64UrlEncode(assertion.response.authenticatorData),
            clientDataJSON: base64UrlEncode(assertion.response.clientDataJSON)
        })
    });

    if (jwtResponse.ok) {
        if (window.parent !== window) {
            window.parent.postMessage("auth_success", `https://${URL_CONFIG.targetOrigin}`);
        } else {
            window.location.href = `https://${URL_CONFIG.targetOrigin}/`;
        }
    } else {
        const errMsg = await jwtResponse.text();
        alert("❌ Errore di autenticazione: " + errMsg);
    }
}