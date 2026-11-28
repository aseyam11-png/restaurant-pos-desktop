import { invoke } from "@tauri-apps/api/core";

export type OrderType = "DINE_IN" | "TAKEAWAY" | "DELIVERY";
export type PaymentMethod = "CASH" | "WALLET" | "BANK_CARD";
export type PreparationStatus = "NEW" | "IN_PREPARATION" | "READY" | "DELIVERED" | "CANCELLED";
export type PaymentStatus = "UNPAID" | "PENDING_CONFIRMATION" | "PARTIALLY_PAID" | "PAID" | "REFUNDED";
export type PricingMode = "FIXED" | "ASK_AT_SALE";

export interface ApiStatus {
  available: boolean;
  message: string;
}

export interface Category {
  id: string;
  name: string;
  sortOrder: number;
  active: boolean;
}

export interface Product {
  id: string;
  productNumber: number;
  categoryId: string;
  name: string;
  unit: string;
  pricingMode: PricingMode;
  price: number | null;
  stockTracked: boolean;
  active: boolean;
}

export interface OrderLine {
  productId: string;
  quantity: number;
  unitPrice?: number;
}

export interface PaymentLine {
  method: PaymentMethod;
  amount: number;
  senderPhone?: string;
}

export interface CreateOrderRequest {
  customerName: string;
  orderType: OrderType;
  lines: OrderLine[];
  discountAmount: number;
  taxAmount: number;
  currency: string;
  payments: PaymentLine[];
}

export interface OrderView {
  id: string;
  orderNumber: number;
  customerName: string;
  orderType: OrderType;
  preparationStatus: PreparationStatus;
  paymentStatus: PaymentStatus;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  total: number;
  currency: string;
  createdAt: string;
  lines: Array<{
    productName: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
  }>;
}

export interface CashShift {
  id: string;
  shiftNumber: number;
  status: "OPEN" | "CLOSED";
  currency: string;
  openingCash: number;
  expectedCash: number;
  countedCash?: number | null;
  variance?: number | null;
  openedAt: string;
  closedAt?: string | null;
}

export interface CustomerOrderDocument {
  orderId: string;
  orderNumber: number;
  customerName: string;
  orderType: OrderType;
  createdAt: string;
  subtotal: number;
  discountAmount: number;
  taxAmount: number;
  total: number;
  currency: string;
  lines: Array<{
    productName: string;
    unit: string;
    quantity: number;
    unitPrice: number;
    lineTotal: number;
  }>;
}

export interface InventoryBalance {
  productId: string;
  productNumber: number;
  productName: string;
  unit: string;
  quantityOnHand: number;
  minimumQuantity: number;
  lowStock: boolean;
}

export interface DiningTable {
  id: string;
  zoneId: string;
  zoneName: string;
  tableNumber: number;
  label: string;
  seats: number;
  status: "AVAILABLE" | "RESERVED" | "OCCUPIED" | "OUT_OF_SERVICE";
  activeOrderId?: string | null;
  activeOrderNumber: number;
}

export interface SalesSummary {
  from: string;
  to: string;
  orders: number;
  cancelledOrders: number;
  orderValue: number;
  confirmedCollections: number;
  pendingConfirmations: number;
  refundedAmount: number;
  netSalesAfterRefunds: number;
  reportingTimeZone: string;
}


export interface Supplier {
  id: string;
  name: string;
  phone?: string | null;
  email?: string | null;
  notes?: string | null;
  active: boolean;
  createdAt: string;
}

export interface PurchaseLineView {
  id: string;
  productId: string;
  productNumber: number;
  productName: string;
  unit: string;
  quantity: number;
  unitCost: number;
  lineTotal: number;
}

export interface PurchaseView {
  id: string;
  purchaseNumber: number;
  supplierId?: string | null;
  status: string;
  paymentStatus: string;
  subtotal: number;
  discountAmount: number;
  total: number;
  paidAmount: number;
  dueAmount: number;
  currency: string;
  paymentMethod?: PaymentMethod | null;
  purchasedAt: string;
  createdBy: string;
  lines: PurchaseLineView[];
}

export interface ExpenseView {
  id: string;
  category: string;
  description: string;
  amount: number;
  currency: string;
  paymentMethod: PaymentMethod;
  cashShiftId?: string | null;
  incurredAt: string;
  createdBy: string;
}

export interface CashMovement {
  id: string;
  movementType: string;
  direction: "IN" | "OUT";
  amount: number;
  description?: string | null;
  actor: string;
  createdAt: string;
}

export type ApiMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

export async function apiStatus(): Promise<ApiStatus> {
  return invoke<ApiStatus>("api_status");
}

export async function apiRequest<T>(
  method: ApiMethod,
  path: string,
  body?: unknown,
  idempotencyKey?: string,
): Promise<T> {
  return invoke<T>("pos_api_request", {
    method,
    path,
    body: body ?? null,
    idempotencyKey: idempotencyKey ?? null,
  });
}

export const posApi = {
  categories: () => apiRequest<Category[]>("GET", "/api/v1/catalog/categories"),
  products: (query = "") => apiRequest<Product[]>(
    "GET",
    `/api/v1/catalog/products${query ? `?query=${encodeURIComponent(query)}` : ""}`,
  ),
  orders: (limit = 100) => apiRequest<OrderView[]>("GET", `/api/v1/orders?limit=${limit}`),
  createOrder: (request: CreateOrderRequest, key: string) =>
    apiRequest<OrderView>("POST", "/api/v1/orders", request, key),
  customerDocument: (id: string) =>
    apiRequest<CustomerOrderDocument>("GET", `/api/v1/orders/${encodeURIComponent(id)}/customer-document`),
  activeCashShift: () => apiRequest<CashShift | null>("GET", "/api/v1/cash-shifts/active"),
  openCashShift: (openingCash: number) =>
    apiRequest<CashShift>("POST", "/api/v1/cash-shifts/open", {
      openingCash,
      openedBy: "Local operator",
    }),
  inventory: (lowStockOnly = false) =>
    apiRequest<InventoryBalance[]>("GET", `/api/v1/inventory/balances?lowStockOnly=${lowStockOnly}`),
  diningTables: () => apiRequest<DiningTable[]>("GET", "/api/v1/dining/tables"),
  salesSummary: (from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    return apiRequest<SalesSummary>("GET", `/api/v1/reports/sales/summary?${params.toString()}`);
  },
  suppliers: () => apiRequest<Supplier[]>("GET", "/api/v1/suppliers"),
  createSupplier: (body: { name: string; phone?: string; email?: string; notes?: string }) =>
    apiRequest<Supplier>("POST", "/api/v1/suppliers", body),
  purchases: (limit = 100) => apiRequest<PurchaseView[]>("GET", `/api/v1/purchases?limit=${limit}`),
  createPurchase: (body: unknown, key: string) =>
    apiRequest<PurchaseView>("POST", "/api/v1/purchases", body, key),
  supplierPayment: (purchaseId: string, body: { amount: number; method: PaymentMethod; recordedBy: string }, key: string) =>
    apiRequest<unknown>("POST", `/api/v1/purchases/${encodeURIComponent(purchaseId)}/payments`, body, key),
  expenses: (limit = 100) => apiRequest<ExpenseView[]>("GET", `/api/v1/expenses?limit=${limit}`),
  createExpense: (body: unknown, key: string) =>
    apiRequest<ExpenseView>("POST", "/api/v1/expenses", body, key),
  cashMovements: (shiftId: string) =>
    apiRequest<CashMovement[]>("GET", `/api/v1/cash-shifts/${encodeURIComponent(shiftId)}/movements?limit=200`),
  addCashMovement: (body: { type: "CASH_IN" | "CASH_OUT"; amount: number; description: string; createdBy: string }) =>
    apiRequest<CashMovement>("POST", "/api/v1/cash-shifts/movements", body),
  closeCashShift: (shiftId: string, countedCash: number) =>
    apiRequest<CashShift>("POST", `/api/v1/cash-shifts/${encodeURIComponent(shiftId)}/close`, {
      countedCash,
      closedBy: "Local operator",
    }),
};
