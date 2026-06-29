/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { request, isMockMode, simulateLatency } from './client';
import { mockDb } from './mockDb';
import { MerchantProfile } from '../types';

export async function getMerchant(merchantId: string): Promise<MerchantProfile> {
  if (isMockMode()) {
    await simulateLatency(150);
    const merchant = mockDb.getMerchantProfile(merchantId);
    if (!merchant) {
      throw new Error('未找到该商家的经营资质记录');
    }
    return merchant;
  }

  return request<MerchantProfile>(`/admin/merchants/${merchantId}`);
}

export async function reviewMerchant(
  merchantId: string,
  approved: boolean,
  reason?: string,
): Promise<MerchantProfile> {
  if (isMockMode()) {
    await simulateLatency(200);
    const merchant = mockDb.reviewMerchant(merchantId, approved, reason);
    if (!merchant) {
      throw new Error('未找到该商家的经营资质记录');
    }
    return merchant;
  }

  return request<MerchantProfile>(`/admin/merchants/${merchantId}/review`, {
    method: 'POST',
    body: JSON.stringify({ approved, reason }),
  });
}
