import * as authService from "../services/auth.service.js";

export async function syncProfile(req, res) {
  const user = await authService.syncUserProfile({
    firebaseUid: req.user.firebaseUid,
    email: req.user.email,
    displayName: req.validated.body.displayName || req.user.displayName,
    phoneNumber: req.validated.body.phoneNumber || req.user.phoneNumber
  });

  res.json({
    success: true,
    data: user
  });
}
