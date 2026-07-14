// /js/deno-patch.js
(function() {
    console.log("[Deno Client] Patch WebAuthn di Debug attiva!");

    // Mappiamo correttamente i metodi originali per evitare ReferenceError
    const originalCreate = navigator.credentials?.create ? navigator.credentials.create.bind(navigator.credentials) : null;
    const originalGet = navigator.credentials?.get ? navigator.credentials.get.bind(navigator.credentials) : null;
    const denoBridge = window.saveKeyToDisk ? window : (window.parent && window.parent.saveKeyToDisk ? window.parent : window);

    function bufferToBase64Url(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) binary += String.fromCharCode(bytes[i]);
        return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
    }

    function base64UrlToBuffer(base64Url) {
        // 1. Ripristina l'imbottitura '=' e i caratteri standard Base64 (+ e /)
        let base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        while (base64.length % 4) {
            base64 += '=';
        }
        // 2. Decodifica la stringa in binario
        const binaryStr = atob(base64);
        // 3. Trasforma la stringa binaria in un Uint8Array / ArrayBuffer
        const bytes = new Uint8Array(binaryStr.length);
        for (let i = 0; i < binaryStr.length; i++) {
            bytes[i] = binaryStr.charCodeAt(i);
        }
        return bytes.buffer;
    }

    async function buildFakeAttestationObject(publicKeyRaw, credIdUint8) {
        const x = publicKeyRaw.slice(1, 33);
        const y = publicKeyRaw.slice(33, 65);

        const domainBuffer = new TextEncoder().encode(`${URL_CONFIG.origin}`);
        const rpIdHashBuffer = await window.crypto.subtle.digest("SHA-256", domainBuffer);
        const rpIdHash = new Uint8Array(rpIdHashBuffer);

        const flags = new Uint8Array([0x45]);
        const signCount = new Uint8Array([0, 0, 0, 1]);
        const aaguid = new Uint8Array(16);
        const credIdLen = new Uint8Array([0, 16]);

        const coseKey = new Uint8Array([
            0xa5,
            0x01, 0x02,
            0x03, 0x26,
            0x20, 0x01,
            0x21, 0x58, 0x20, ...x,
            0x22, 0x58, 0x20, ...y
        ]);

        const authData = new Uint8Array([
            ...rpIdHash, ...flags, ...signCount,
            ...aaguid, ...credIdLen, ...credIdUint8,
            ...coseKey
        ]);

        const attestationCBOR = new Uint8Array([
            0xa3,
            0x63, 0x66, 0x6d, 0x74, 0x64, 0x6e, 0x6f, 0x6e, 0x65,
            0x67, 0x61, 0x74, 0x74, 0x53, 0x74, 0x6d, 0x74, 0xa0,
            0x68, 0x61, 0x75, 0x74, 0x68, 0x44, 0x61, 0x74, 0x61,
            0x58, authData.length, ...authData
        ]);

        return attestationCBOR.buffer;
    }

    if (navigator.credentials) {
        // --- INTERCETTAZIONE REGISTRAZIONE ---
        navigator.credentials.create = async function(options) {
            if (!options.publicKey) {
                if (originalCreate) return originalCreate(options);
                throw new Error("originalCreate non disponibile");
            }

            console.log("[Deno Patch] 1. navigator.credentials.create intercettato con successo!");

            try {
                console.log("[Deno Patch] 2. Generazione coppia di chiavi asimmetriche...");
                const keyPair = await window.crypto.subtle.generateKey(
                    { name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]
                );

                console.log("[Deno Patch] 3. Chiavi generate. Esporto chiave privata in formato JWK...");
                const privateKeyJwk = await window.crypto.subtle.exportKey("jwk", keyPair.privateKey);

                const publicKeyRaw = new Uint8Array(await window.crypto.subtle.exportKey("raw", keyPair.publicKey));
                const credIdUint8 = window.crypto.getRandomValues(new Uint8Array(16));
                const credIdB64Url = bufferToBase64Url(credIdUint8.buffer);

                const payloadToSave = {
                    credentialId: credIdB64Url,
                    privateKey: privateKeyJwk,
                    signCount: 0
                };
                denoBridge.saveKeyToDisk(payloadToSave);

                const originalChallengeB64Url = bufferToBase64Url(options.publicKey.challenge);

                const clientData = {
                    type: "webauthn.create",
                    challenge: originalChallengeB64Url,
                    origin: `https://${URL_CONFIG.origin}`
                };

                const clientDataJSON = new TextEncoder().encode(JSON.stringify(clientData));
                const attestationObject = await buildFakeAttestationObject(publicKeyRaw, credIdUint8);

                console.log("[Deno Patch] 7. Generazione completata. Restituisco l'oggetto credenziale al frontend.");
                return {
                    id: credIdB64Url,
                    rawId: credIdUint8.buffer,
                    type: "public-key",
                    response: {
                        clientDataJSON: clientDataJSON.buffer,
                        attestationObject: attestationObject,
                        getTransports: () => ["internal"]
                    },
                    getClientExtensionResults: () => ({})
                };
            } catch (err) {
                console.error("[Deno Patch] ❌ ERRORE CRITICO in credentials.create:", err);
                alert("Errore interno alla Patch: " + err.message);
                throw err;
            }
        };

        // --- INTERCETTAZIONE LOGIN ---
        navigator.credentials.get = async function(options) {
            if (!options.publicKey) {
                if (originalGet) return originalGet(options);
                throw new Error("originalGet non disponibile");
            }
            console.log("[Deno Patch] Entrato in navigator.credentials.get");

            try {
                let savedData = null;
                const isIframe = window.parent !== window;

                if (isIframe) {
                    console.log("[Deno Patch] Rilevato iframe Cross-Origin. Richiedo la chiave tramite postMessage...");

                    // Invia la richiesta al padre
                    window.parent.postMessage({ type: "REQUEST_KEY_FROM_DISK" }, `https://${URL_CONFIG.targetOrigin}`);

                    // Restiamo in ascolto della risposta dal padre usando una Promise
                    savedData = await new Promise((resolve) => {
                        const handleMessage = (event) => {
                            if (event.origin !== `https://${URL_CONFIG.targetOrigin}`) return;
                            if (event.data.type === "RESPONSE_KEY_FROM_DISK") {
                                window.removeEventListener("message", handleMessage);
                                resolve(event.data.data);
                            }
                        };
                        window.addEventListener("message", handleMessage);
                    });
                } else {
                    console.log("[Deno Patch] Contesto nativo top-level. Lettura diretta...");
                    savedData = await window.readKeyFromDisk();
                }

                if (!savedData) {
                    alert("Nessuna passkey trovata sul disco! Registra prima il dispositivo.");
                    throw new Error("No local key");
                }

                const privateKeyJwk = savedData.privateKey;
                const savedCredentialIdB64Url = savedData.credentialId;
                let currentCount = savedData.signCount || 0;
                currentCount += 1;

                // Convertiamo la stringa salvata nell'ArrayBuffer richiesto da WebAuthn
                const rawIdBuffer = base64UrlToBuffer(savedCredentialIdB64Url);
                const credentialIdB64Url = savedCredentialIdB64Url;

                if (!privateKeyJwk) {
                    alert("Nessuna passkey trovata sul disco! Registra prima il dispositivo.");
                    throw new Error("No local key");
                }

                const privateKey = await window.crypto.subtle.importKey(
                    "jwk", privateKeyJwk, { name: "ECDSA", namedCurve: "P-256" }, true, ["sign"]
                );

                const originalChallengeB64Url = bufferToBase64Url(options.publicKey.challenge);

                const clientData = {
                    type: "webauthn.get",
                    challenge: originalChallengeB64Url,
                    origin: `https://${URL_CONFIG.origin}`
                };
                const clientDataText = JSON.stringify(clientData);
                const clientDataUint8 = new TextEncoder().encode(clientDataText);

                const clientDataHash = await window.crypto.subtle.digest("SHA-256", clientDataUint8);

                // 1. PRIMA prepariamo l'authenticatorData
                const rpIdHashBuffer = await window.crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${URL_CONFIG.origin}`));
                const authDataArray = new Uint8Array(37);
                authDataArray.set(new Uint8Array(rpIdHashBuffer), 0);
                authDataArray[32] = 0x05; // UP + UV
                authDataArray[33] = (currentCount >> 24) & 0xFF;
                authDataArray[34] = (currentCount >> 16) & 0xFF;
                authDataArray[35] = (currentCount >> 8) & 0xFF;
                authDataArray[36] = currentCount & 0xFF;

                // 2. POI concateniamo authData + clientDataHash
                const dataToSign = new Uint8Array(authDataArray.length + clientDataHash.byteLength);
                dataToSign.set(authDataArray, 0);
                dataToSign.set(new Uint8Array(clientDataHash), authDataArray.length);

                // 3. E INFINE firmiamo l'intero blocco unito
                const signatureRaw = await window.crypto.subtle.sign(
                    { name: "ECDSA", hash: { name: "SHA-256" } }, privateKey, dataToSign.buffer
                );

                const signatureDer = rawSignatureToDer(signatureRaw);

                const updatedPayload = {
                    credentialId: savedCredentialIdB64Url,
                    privateKey: privateKeyJwk,
                    signCount: currentCount
                };

                // MODIFICA ANCHE IL SALVATAGGIO IN FONDO AL LOGIN:
                if (isIframe) {
                    window.parent.postMessage({ type: "SAVE_KEY_TO_DISK", payload: updatedPayload }, `https://${URL_CONFIG.targetOrigin}`);
                } else {
                    denoBridge.saveKeyToDisk(updatedPayload);
                }

                await new Promise(resolve => setTimeout(resolve, 50));

// --- FUNZIONE DI CONVERSIONE RAW -> DER ---
                function rawSignatureToDer(rawBuffer) {
                    const raw = new Uint8Array(rawBuffer);
                    if (raw.length !== 64) return raw; // Se è già manipolata, ritorna

                    const r = raw.slice(0, 32);
                    const s = raw.slice(32, 64);

                    // Rimuove gli zeri iniziali superflui ma mantiene il segno per ASN.1 (regola del complemento a due)
                    function cleanComponent(component) {
                        let start = 0;
                        while (start < component.length - 1 && component[start] === 0) start++;
                        if ((component[start] & 0x80) !== 0) {
                            // Se il bit più significativo è 1, serve un byte 0x00 iniziale per indicare che è positivo
                            const res = new Uint8Array(component.length - start + 1);
                            res.set(component.slice(start), 1);
                            return res;
                        } else {
                            return component.slice(start);
                        }
                    }

                    const rClean = cleanComponent(r);
                    const sClean = cleanComponent(s);

                    const totalLength = rClean.length + sClean.length + 4; // 4 byte per i tag e le lunghezze di R e S
                    const der = new Uint8Array(totalLength + 2); // +2 byte per il tag SEQUENCE e la lunghezza totale

                    let pos = 0;
                    der[pos++] = 0x30; // ASN.1 SEQUENCE
                    der[pos++] = totalLength;

                    der[pos++] = 0x02; // ASN.1 INTEGER (R)
                    der[pos++] = rClean.length;
                    der.set(rClean, pos);
                    pos += rClean.length;

                    der[pos++] = 0x02; // ASN.1 INTEGER (S)
                    der[pos++] = sClean.length;
                    der.set(sClean, pos);

                    return der.buffer;
                }

                return {
                    id: credentialIdB64Url,
                    rawId: rawIdBuffer,
                    type: "public-key",
                    response: {
                        clientDataJSON: clientDataUint8.buffer,
                        signature: signatureDer,
                        authenticatorData: authDataArray.buffer
                    },
                    getClientExtensionResults: () => ({})
                };
            } catch (err) {
                console.error("[Deno Patch] ❌ ERRORE CRITICO in credentials.get:", err);
                throw err;
            }
        };
    }
})();