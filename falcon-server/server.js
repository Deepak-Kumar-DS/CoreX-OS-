const express = require("express");
const cors = require("cors");
const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const multer = require("multer");

const app = express();
const PORT = process.env.PORT || 5001;

const DATA_FILE = path.join(__dirname, "falcon-data.json");
const UPLOAD_DIR = path.join(__dirname, "falcon-uploads");

const SESSION_DURATION = 30 * 24 * 60 * 60 * 1000;

if (!fs.existsSync(UPLOAD_DIR)) {
    fs.mkdirSync(UPLOAD_DIR, { recursive: true });
}

app.use(cors());
app.use(express.json({ limit: "2mb" }));

// ======================================================
// DATABASE
// ======================================================

function loadData() {
    try {
        const data = JSON.parse(
            fs.readFileSync(DATA_FILE, "utf8")
        );

        return {
            ...data,
            users: data.users || {},
            messages: data.messages || [],
            files: data.files || [],
            pulseUsers: data.pulseUsers || {},
            pulseMessages: data.pulseMessages || [],
            pulseSessions: data.pulseSessions || {}
        };

    } catch {
        return {
            users: {},
            messages: [],
            files: [],
            pulseUsers: {},
            pulseMessages: [],
            pulseSessions: {}
        };
    }
}

function saveData(data) {
    const temporaryFile = DATA_FILE + ".tmp";

    fs.writeFileSync(
        temporaryFile,
        JSON.stringify(data, null, 2),
        { mode: 0o600 }
    );

    fs.renameSync(temporaryFile, DATA_FILE);
}

// ======================================================
// COMMON HELPERS
// ======================================================

function sendError(res, status, message) {
    return res.status(status).json({
        success: false,
        error: message
    });
}

function normalizeEmail(value) {
    return String(value || "").trim().toLowerCase();
}

function normalizePhone(value) {
    return String(value || "").replace(/[\s()-]/g, "");
}

function hashToken(token) {
    return crypto
        .createHash("sha256")
        .update(token)
        .digest("hex");
}

function validatePublicKey(publicKey) {
    try {
        if (
            typeof publicKey !== "string" ||
            publicKey.length < 100 ||
            publicKey.length > 8192
        ) {
            return false;
        }

        const decoded = Buffer.from(publicKey, "base64");

        if (decoded.length === 0 || decoded.length > 8192) {
            return false;
        }

        const key = crypto.createPublicKey({
            key: decoded,
            format: "der",
            type: "spki"
        });

        return (
            key.asymmetricKeyType === "rsa" &&
            key.asymmetricKeyDetails.modulusLength >= 2048
        );

    } catch {
        return false;
    }
}

// ======================================================
// PASSWORD SECURITY
// ======================================================

function createPasswordHash(password, salt) {
    return crypto.scryptSync(
        password,
        salt,
        64
    ).toString("hex");
}

function verifyPassword(password, salt, storedHash) {
    try {
        const calculated = Buffer.from(
            createPasswordHash(password, salt),
            "hex"
        );

        const stored = Buffer.from(storedHash, "hex");

        return (
            calculated.length === stored.length &&
            crypto.timingSafeEqual(calculated, stored)
        );

    } catch {
        return false;
    }
}

// ======================================================
// COREX PULSE AUTHENTICATION
// ======================================================

function authenticatePulse(req, res, next) {
    const authorization = req.headers.authorization || "";

    if (!authorization.startsWith("Bearer ")) {
        return sendError(
            res,
            401,
            "Authentication required."
        );
    }

    const token = authorization.substring(7).trim();

    if (!token || token.length > 256) {
        return sendError(
            res,
            401,
            "Invalid authentication token."
        );
    }

    const tokenHash = hashToken(token);
    const data = loadData();

    const session = data.pulseSessions[tokenHash];

    if (!session) {
        return sendError(
            res,
            401,
            "Session expired. Please login again."
        );
    }

    if (session.expiresAt <= Date.now()) {
        delete data.pulseSessions[tokenHash];
        saveData(data);

        return sendError(
            res,
            401,
            "Session expired. Please login again."
        );
    }

    if (!data.pulseUsers[session.userId]) {
        return sendError(
            res,
            401,
            "Account not found."
        );
    }

    req.pulseUserId = session.userId;
    next();
}

function createPulseSession(userId) {
    const data = loadData();

    const token = crypto.randomBytes(48).toString("hex");
    const tokenHash = hashToken(token);

    data.pulseSessions[tokenHash] = {
        userId,
        createdAt: Date.now(),
        expiresAt: Date.now() + SESSION_DURATION
    };

    saveData(data);

    return token;
}

// ======================================================
// SERVER HEALTH
// ======================================================

app.get("/api/health", (req, res) => {
    res.json({
        success: true,
        service: "CoreX Communication Server",
        status: "running",
        services: [
            "Falcon Relay",
            "CoreX Pulse"
        ]
    });
});

// ======================================================
// FALCON APIs — EXISTING FUNCTIONALITY
// ======================================================

// REGISTER FALCON USER

app.post("/api/register", (req, res) => {
    const { falconId, publicKey } = req.body;

    if (!falconId || !publicKey) {
        return sendError(
            res,
            400,
            "Falcon ID and public key required."
        );
    }

    const data = loadData();

    if (data.users[falconId]) {
        return sendError(
            res,
            409,
            "Falcon ID already registered."
        );
    }

    data.users[falconId] = {
        publicKey,
        createdAt: new Date().toISOString()
    };

    saveData(data);

    res.json({
        success: true,
        falconId
    });
});

// FIND FALCON USER

app.get("/api/users/:falconId", (req, res) => {
    const data = loadData();
    const user = data.users[req.params.falconId];

    if (!user) {
        return sendError(res, 404, "User not found.");
    }

    res.json({
        success: true,
        falconId: req.params.falconId,
        publicKey: user.publicKey
    });
});

// SEND FALCON MESSAGE

app.post("/api/messages", (req, res) => {
    const {
        senderId,
        recipientId,
        encryptedPayload
    } = req.body;

    if (!senderId || !recipientId || !encryptedPayload) {
        return sendError(
            res,
            400,
            "Required fields missing."
        );
    }

    const data = loadData();

    if (!data.users[senderId] || !data.users[recipientId]) {
        return sendError(
            res,
            404,
            "User not registered."
        );
    }

    const message = {
        messageId: crypto.randomUUID(),
        senderId,
        recipientId,
        encryptedPayload,
        createdAt: new Date().toISOString(),
        delivered: false
    };

    data.messages.push(message);
    saveData(data);

    res.status(201).json({
        success: true,
        messageId: message.messageId,
        status: "queued"
    });
});

// FALCON INBOX

app.get("/api/inbox/:falconId", (req, res) => {
    const data = loadData();
    const falconId = req.params.falconId;

    if (!data.users[falconId]) {
        return sendError(
            res,
            404,
            "User not registered."
        );
    }

    const messages = data.messages.filter(message =>
        message.recipientId === falconId &&
        !message.delivered
    );

    const files = data.files
        .filter(file =>
            file.recipientId === falconId &&
            !file.delivered
        )
        .map(file => ({
            fileId: file.fileId,
            senderId: file.senderId,
            recipientId: file.recipientId,
            originalName: file.originalName,
            mimeType: file.mimeType,
            size: file.size,
            downloadUrl: `/api/files/${file.fileId}`,
            createdAt: file.createdAt
        }));

    res.json({
        success: true,
        messages,
        files
    });
});

// ACKNOWLEDGE FALCON MESSAGE

app.post("/api/messages/:messageId/ack", (req, res) => {
    const data = loadData();

    const message = data.messages.find(
        item => item.messageId === req.params.messageId
    );

    if (!message) {
        return sendError(
            res,
            404,
            "Message not found."
        );
    }

    message.delivered = true;
    saveData(data);

    res.json({ success: true });
});

// ======================================================
// FALCON FILE TRANSFER
// ======================================================

const storage = multer.diskStorage({
    destination: (req, file, callback) => {
        callback(null, UPLOAD_DIR);
    },

    filename: (req, file, callback) => {
        callback(null, crypto.randomUUID() + ".encrypted");
    }
});

const upload = multer({
    storage,
    limits: {
        fileSize: 100 * 1024 * 1024
    }
});

app.post("/api/files", upload.single("file"), (req, res) => {
    try {
        if (!req.file) {
            return sendError(res, 400, "File is required.");
        }

        const { senderId, recipientId } = req.body;
        const data = loadData();

        if (
            !senderId ||
            !recipientId ||
            !data.users[senderId] ||
            !data.users[recipientId]
        ) {
            fs.unlinkSync(req.file.path);

            return sendError(
                res,
                400,
                "Valid sender and recipient IDs required."
            );
        }

        const fileRecord = {
            fileId: path.basename(
                req.file.filename,
                ".encrypted"
            ),
            senderId,
            recipientId,
            originalName: path.basename(req.file.originalname),
            mimeType: req.file.mimetype,
            size: req.file.size,
            storedName: req.file.filename,
            createdAt: new Date().toISOString(),
            delivered: false
        };

        data.files.push(fileRecord);
        saveData(data);

        res.status(201).json({
            success: true,
            fileId: fileRecord.fileId,
            status: "queued"
        });

    } catch (error) {
        console.error(error);

        res.status(500).json({
            success: false,
            error: "File upload failed."
        });
    }
});

app.get("/api/files/:fileId", (req, res) => {
    const data = loadData();

    const file = data.files.find(
        item => item.fileId === req.params.fileId
    );

    if (!file) {
        return sendError(res, 404, "File not found.");
    }

    const filePath = path.join(
        UPLOAD_DIR,
        file.storedName
    );

    if (!fs.existsSync(filePath)) {
        return sendError(
            res,
            404,
            "Stored file not found."
        );
    }

    res.download(filePath, file.originalName);
});

app.post("/api/files/:fileId/ack", (req, res) => {
    const data = loadData();

    const file = data.files.find(
        item => item.fileId === req.params.fileId
    );

    if (!file) {
        return sendError(res, 404, "File not found.");
    }

    file.delivered = true;
    saveData(data);

    res.json({ success: true });
});

// ======================================================
// COREX PULSE — REGISTRATION
// ======================================================

app.post("/api/pulse/register", (req, res) => {
    try {
        const {
            displayName,
            email,
            phone,
            password,
            publicKey
        } = req.body;

        const cleanName = String(
            displayName || ""
        ).trim();

        const cleanEmail = normalizeEmail(email);
        const cleanPhone = normalizePhone(phone);

        if (
            cleanName.length < 2 ||
            cleanName.length > 40
        ) {
            return sendError(
                res,
                400,
                "Name must contain 2 to 40 characters."
            );
        }

        // Exactly one login identifier is required.
        if (!!cleanEmail === !!cleanPhone) {
            return sendError(
                res,
                400,
                "Provide either email or phone number."
            );
        }

        if (cleanEmail) {
            const emailPattern =
                /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

            if (!emailPattern.test(cleanEmail)) {
                return sendError(
                    res,
                    400,
                    "Invalid email address."
                );
            }
        }

        if (cleanPhone) {
            const phonePattern = /^\+?[0-9]{8,15}$/;

            if (!phonePattern.test(cleanPhone)) {
                return sendError(
                    res,
                    400,
                    "Phone number must contain 8 to 15 digits."
                );
            }
        }

        if (
            typeof password !== "string" ||
            password.length < 10 ||
            password.length > 128
        ) {
            return sendError(
                res,
                400,
                "Password must contain 10 to 128 characters."
            );
        }

        if (!validatePublicKey(publicKey)) {
            return sendError(
                res,
                400,
                "A valid RSA public key of at least 2048 bits is required."
            );
        }

        const data = loadData();

        const duplicate = Object.values(
            data.pulseUsers
        ).some(user =>
            (cleanEmail && user.email === cleanEmail) ||
            (cleanPhone && user.phone === cleanPhone)
        );

        if (duplicate) {
            return sendError(
                res,
                409,
                "This email or phone number is already registered."
            );
        }

        const userId =
            "pulse_" + crypto.randomUUID().replace(/-/g, "");

        const salt = crypto.randomBytes(16).toString("hex");

        const passwordHash = createPasswordHash(
            password,
            salt
        );

        data.pulseUsers[userId] = {
            userId,
            displayName: cleanName,
            email: cleanEmail || null,
            phone: cleanPhone || null,
            passwordSalt: salt,
            passwordHash,
            publicKey,
            createdAt: new Date().toISOString(),
            verified: false
        };

        saveData(data);

        const token = createPulseSession(userId);

        res.status(201).json({
            success: true,
            user: {
                userId,
                displayName: cleanName,
                email: cleanEmail || null,
                phone: cleanPhone || null,
                publicKey,
                verified: false
            },
            token,
            verificationStatus: "pending"
        });

    } catch (error) {
        console.error("Pulse registration error:", error);

        return sendError(
            res,
            500,
            "Registration failed."
        );
    }
});

// ======================================================
// COREX PULSE — LOGIN
// ======================================================

app.post("/api/pulse/login", (req, res) => {
    try {
        const { identifier, password } = req.body;

        if (
            typeof identifier !== "string" ||
            typeof password !== "string"
        ) {
            return sendError(
                res,
                400,
                "Login details are required."
            );
        }

        const cleanIdentifier = identifier.includes("@")
            ? normalizeEmail(identifier)
            : normalizePhone(identifier);

        const data = loadData();

        const user = Object.values(
            data.pulseUsers
        ).find(item =>
            item.email === cleanIdentifier ||
            item.phone === cleanIdentifier
        );

        if (
            !user ||
            !verifyPassword(
                password,
                user.passwordSalt,
                user.passwordHash
            )
        ) {
            return sendError(
                res,
                401,
                "Invalid email/phone or password."
            );
        }

        const token = createPulseSession(user.userId);

        res.json({
            success: true,
            token,
            user: {
                userId: user.userId,
                displayName: user.displayName,
                email: user.email,
                phone: user.phone,
                publicKey: user.publicKey,
                verified: user.verified
            }
        });

    } catch (error) {
        console.error("Pulse login error:", error);

        return sendError(
            res,
            500,
            "Login failed."
        );
    }
});

// ======================================================
// COREX PULSE — CURRENT ACCOUNT
// ======================================================

app.get(
    "/api/pulse/me",
    authenticatePulse,
    (req, res) => {
        const data = loadData();
        const user = data.pulseUsers[req.pulseUserId];

        res.json({
            success: true,
            user: {
                userId: user.userId,
                displayName: user.displayName,
                email: user.email,
                phone: user.phone,
                publicKey: user.publicKey,
                verified: user.verified
            }
        });
    }
);

// ======================================================
// COREX PULSE — LOGOUT
// ======================================================

app.post(
    "/api/pulse/logout",
    authenticatePulse,
    (req, res) => {
        const authorization =
            req.headers.authorization || "";

        const token = authorization.substring(7).trim();
        const tokenHash = hashToken(token);

        const data = loadData();

        delete data.pulseSessions[tokenHash];

        saveData(data);

        res.json({
            success: true,
            message: "Logged out."
        });
    }
);

// ======================================================
// COREX PULSE — SEARCH USERS
// ======================================================

app.get(
    "/api/pulse/users/search",
    authenticatePulse,
    (req, res) => {
        const query = String(
            req.query.q || ""
        ).trim().toLowerCase();

        if (query.length < 2 || query.length > 80) {
            return sendError(
                res,
                400,
                "Search query must contain 2 to 80 characters."
            );
        }

        const data = loadData();

        const results = Object.values(
            data.pulseUsers
        )
            .filter(user => {
                if (user.userId === req.pulseUserId) {
                    return false;
                }

                return (
                    user.displayName.toLowerCase().includes(query) ||
                    (user.email || "").includes(query) ||
                    (user.phone || "").includes(query)
                );
            })
            .slice(0, 20)
            .map(user => ({
                userId: user.userId,
                displayName: user.displayName,
                publicKey: user.publicKey
            }));

        res.json({
            success: true,
            users: results
        });
    }
);

// ======================================================
// COREX PULSE — SEND ENCRYPTED MESSAGE
// ======================================================

app.post(
    "/api/pulse/messages",
    authenticatePulse,
    (req, res) => {
        try {
            const {
                recipientId,
                encryptedPayload
            } = req.body;

            if (
                typeof recipientId !== "string" ||
                typeof encryptedPayload !== "string"
            ) {
                return sendError(
                    res,
                    400,
                    "Recipient and encrypted message are required."
                );
            }

            if (
                encryptedPayload.length < 20 ||
                encryptedPayload.length > 2 * 1024 * 1024
            ) {
                return sendError(
                    res,
                    400,
                    "Encrypted message size is invalid."
                );
            }

            const envelope = JSON.parse(encryptedPayload);

            if (
                envelope.senderId !== req.pulseUserId ||
                envelope.recipientId !== recipientId
            ) {
                return sendError(
                    res,
                    400,
                    "Message identity does not match."
                );
            }

            const data = loadData();

            if (!data.pulseUsers[recipientId]) {
                return sendError(
                    res,
                    404,
                    "Recipient account not found."
                );
            }

            const message = {
                messageId: crypto.randomUUID(),
                senderId: req.pulseUserId,
                recipientId,
                encryptedPayload,
                createdAt: new Date().toISOString(),
                delivered: false
            };

            data.pulseMessages.push(message);
            saveData(data);

            res.status(201).json({
                success: true,
                messageId: message.messageId,
                status: "queued"
            });

        } catch (error) {
            return sendError(
                res,
                400,
                "Invalid encrypted message."
            );
        }
    }
);

// ======================================================
// COREX PULSE — INBOX
// ======================================================

app.get(
    "/api/pulse/inbox",
    authenticatePulse,
    (req, res) => {
        const data = loadData();

        const messages = data.pulseMessages
            .filter(message =>
                message.recipientId === req.pulseUserId &&
                !message.delivered
            )
            .slice(0, 100);

        res.json({
            success: true,
            messages
        });
    }
);

// ======================================================
// COREX PULSE — ACKNOWLEDGE MESSAGE
// ======================================================

app.post(
    "/api/pulse/messages/:messageId/ack",
    authenticatePulse,
    (req, res) => {
        const data = loadData();

        const message = data.pulseMessages.find(
            item =>
                item.messageId === req.params.messageId &&
                item.recipientId === req.pulseUserId
        );

        if (!message) {
            return sendError(
                res,
                404,
                "Message not found."
            );
        }

        message.delivered = true;
        message.deliveredAt = new Date().toISOString();

        saveData(data);

        res.json({
            success: true
        });
    }
);

// ======================================================
// ERROR HANDLER
// ======================================================

app.use((error, req, res, next) => {
    console.error(error);

    res.status(
        error.code === "LIMIT_FILE_SIZE" ? 413 : 500
    ).json({
        success: false,
        error:
            error.code === "LIMIT_FILE_SIZE"
                ? "Maximum file size is 100 MB."
                : "Server error."
    });
});

// ======================================================
// START SERVER
// ======================================================

app.listen(PORT, "0.0.0.0", () => {
    console.log("------------------------------------");
    console.log("CoreX Communication Server");
    console.log("Falcon Relay: ENABLED");
    console.log("CoreX Pulse API: ENABLED");
    console.log(`Server port: ${PORT}`);
    console.log("------------------------------------");
});