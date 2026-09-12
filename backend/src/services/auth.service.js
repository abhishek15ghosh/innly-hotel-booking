import { withTransaction } from "../config/db.js";
import { upsertUser } from "../repositories/user.repository.js";

export async function syncUserProfile({ firebaseUid, email, displayName, phoneNumber }, deps = {}) {
  const { withTx = withTransaction, userRepo = { upsertUser } } = deps;
  return withTx(async (client) => {
    return userRepo.upsertUser(client, {
      firebaseUid,
      email,
      displayName,
      phoneNumber
    });
  });
}
