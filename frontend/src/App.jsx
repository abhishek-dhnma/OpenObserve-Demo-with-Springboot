import React, { useState, useEffect } from 'react';
import { ShoppingCart, Search, MapPin, ChevronDown, Menu, Copy, ExternalLink, RefreshCw, AlertTriangle, CheckCircle2, Plus, Minus, Trash2, ArrowLeft, CreditCard, Truck, Check } from 'lucide-react';

const INITIAL_PRODUCTS = [
  {
    id: "PROD-001",
    name: "Echo Dot (5th Gen) Smart Speaker",
    category: "Electronics",
    price: 49.99,
    rating: 4.7,
    stock: 45,
    imageUrl: "https://images.unsplash.com/photo-1543512214-318c7553f230?w=500",
    description: "Vibrant sound smart speaker with Alexa built-in."
  },
  {
    id: "PROD-002",
    name: "Kindle Paperwhite (16 GB)",
    category: "Electronics",
    price: 139.99,
    rating: 4.8,
    stock: 18,
    imageUrl: "https://images.unsplash.com/photo-1592478411213-6153e4ebc07d?w=500",
    description: "6.8 display with thinner borders and warm light feature."
  },
  {
    id: "PROD-003",
    name: "Fire TV Stick 4K Max",
    category: "Electronics",
    price: 59.99,
    rating: 4.6,
    stock: 3,
    imageUrl: "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=500",
    description: "Cinematic 4K streaming with Wi-Fi 6 support."
  },
  {
    id: "PROD-004",
    name: "Sony WH-1000XM5 Wireless Headphones",
    category: "Audio",
    price: 398.00,
    rating: 4.9,
    stock: 25,
    imageUrl: "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=500",
    description: "Industry-leading noise canceling with Auto NC Optimizer."
  },
  {
    id: "PROD-005",
    name: "Apple iPad Air (5th Gen)",
    category: "Computers",
    price: 599.00,
    rating: 4.8,
    stock: 12,
    imageUrl: "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?w=500",
    description: "Supercharged by the Apple M1 chip."
  },
  {
    id: "PROD-006",
    name: "Logitech MX Master 3S Wireless Mouse",
    category: "Computers",
    price: 99.99,
    rating: 4.7,
    stock: 50,
    imageUrl: "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=500",
    description: "Performance wireless mouse with 8K DPI track-on-glass sensor."
  }
];

export default function App() {
  const [view, setView] = useState('HOME'); // HOME, CHECKOUT, SUMMARY
  const [products, setProducts] = useState(INITIAL_PRODUCTS);
  const [cart, setCart] = useState([]);
  const [isCartOpen, setIsCartOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('All');
  const [lastTrace, setLastTrace] = useState(null);
  const [lastOrder, setLastOrder] = useState(null);
  const [loading, setLoading] = useState(false);
  const [simulationMode, setSimulationMode] = useState('NONE'); // NONE, INVENTORY, PAYMENT, SHIPPING
  const [copied, setCopied] = useState(false);

  const [shippingAddress, setShippingAddress] = useState({
    fullName: 'Abhishek Dhiman',
    street: 'B-12, Sector 62',
    city: 'New Delhi',
    state: 'Delhi',
    zip: '110059'
  });

  useEffect(() => {
    fetchProducts();
  }, [selectedCategory]);

  const fetchProducts = async () => {
    try {
      let url = '/api/products';
      const params = new URLSearchParams();
      if (searchQuery) params.append('query', searchQuery);
      if (selectedCategory !== 'All') params.append('category', selectedCategory);
      if (params.toString()) url += '?' + params.toString();

      const res = await fetch(url);
      const data = await res.json();
      if (data.success && data.data && data.data.length > 0) {
        setProducts(data.data);
      }
    } catch (err) {
      console.warn("Backend API connecting...", err);
    }
  };

  const addToCart = (product) => {
    setCart(prev => {
      const existing = prev.find(item => item.productId === product.id);
      if (existing) {
        return prev.map(item => item.productId === product.id ? { ...item, quantity: item.quantity + 1 } : item);
      }
      return [...prev, {
        productId: product.id,
        productName: product.name,
        quantity: 1,
        unitPrice: product.price
      }];
    });
  };

  const updateQuantity = (productId, delta) => {
    setCart(prev => prev.map(item => {
      if (item.productId === productId) {
        const newQty = item.quantity + delta;
        return newQty > 0 ? { ...item, quantity: newQty } : null;
      }
      return item;
    }).filter(Boolean));
  };

  const removeFromCart = (productId) => {
    setCart(prev => prev.filter(item => item.productId !== productId));
  };

  const proceedToCheckoutPage = () => {
    if (cart.length === 0) return;
    setIsCartOpen(false);
    setView('CHECKOUT');
  };

  const handleCheckoutSubmit = async () => {
    if (cart.length === 0) return;
    setLoading(true);

    try {
      const payload = {
        customerEmail: 'abhishek@example.com',
        items: cart,
        paymentMethod: simulationMode,
        simulateFailure: simulationMode !== 'NONE'
      };

      const res = await fetch('/api/orders/checkout', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });

      const data = await res.json();

      const traceInfo = {
        type: data.success ? 'ORDER_SUCCESS' : 'ORDER_FAILURE',
        title: data.success ? 'Order Placed Successfully!' : 'Order Processing Failed',
        message: data.message,
        traceId: data.traceId || 'trace-demo-' + Date.now(),
        spanId: data.spanId || 'span-demo-' + Date.now(),
        orderId: data.data ? data.data.orderId : 'ORD-' + Date.now()
      };

      setLastTrace(traceInfo);
      setLastOrder({
        ...data.data,
        items: [...cart],
        shippingAddress,
        totalAmount: cartTotal,
        traceId: traceInfo.traceId
      });

      if (data.success) {
        setCart([]);
        setView('SUMMARY');
        fetchProducts();
      }
    } catch (err) {
      console.error("Checkout API error:", err);
    } finally {
      setLoading(false);
    }
  };

  const triggerTrafficSimulation = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/simulate/traffic?count=6', { method: 'POST' });
      const data = await res.json();
      setLastTrace({
        type: 'SIMULATION',
        title: 'Traffic Generator Executed',
        message: data.message,
        traceId: data.traceId,
        spanId: data.spanId
      });
      fetchProducts();
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const triggerErrorSimulation = async () => {
    try {
      const res = await fetch('/api/simulate/error', { method: 'POST' });
      const data = await res.json();
      setLastTrace({
        type: 'SYSTEM_ERROR',
        title: 'System Failure Simulated',
        message: data.message,
        traceId: data.traceId,
        spanId: data.spanId
      });
    } catch (err) {
      console.error(err);
    }
  };

  const copyTraceId = (id) => {
    navigator.clipboard.writeText(id);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const totalCartCount = cart.reduce((sum, item) => sum + item.quantity, 0);
  const cartTotal = cart.reduce((sum, item) => sum + (item.unitPrice * item.quantity), 0);

  return (
    <div>
      {/* Header */}
      <header className="amz-header">
        <div className="amz-logo" onClick={() => setView('HOME')}>
          <span>AmzStore</span><span className="amz-logo-accent">.in</span>
        </div>

        {/* Location Selector */}
        <div className="amz-location">
          <MapPin size={16} color="#fff" />
          <div className="amz-loc-text">
            <span style={{ color: '#ccc' }}>Deliver to Abhishek</span><br />
            <span className="amz-loc-bold">New Delhi 110059</span>
          </div>
        </div>

        {/* Search Bar */}
        <div className="amz-search">
          <select className="amz-search-cat" value={selectedCategory} onChange={(e) => setSelectedCategory(e.target.value)}>
            <option value="All">All Categories</option>
            <option value="Electronics">Electronics</option>
            <option value="Audio">Audio</option>
            <option value="Computers">Computers</option>
          </select>
          <input
            type="text"
            placeholder="Search AmzStore"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && fetchProducts()}
          />
          <button className="amz-search-btn" onClick={fetchProducts}>
            <Search size={20} />
          </button>
        </div>

        {/* Right Navigation */}
        <div className="amz-nav-right">
          <div className="amz-nav-item">
            <span style={{ color: '#ccc' }}>EN</span>
          </div>

          <div className="amz-nav-item">
            <span>Hello, Abhishek</span>
            <strong>Account & Lists <ChevronDown size={12} style={{ display: 'inline' }} /></strong>
          </div>

          <div className="amz-nav-item">
            <span>Returns</span>
            <strong>& Orders</strong>
          </div>

          <div className="amz-cart-btn" onClick={() => setIsCartOpen(true)}>
            <ShoppingCart size={28} color="#fff" />
            {totalCartCount > 0 && (
              <span className="amz-cart-count">{totalCartCount}</span>
            )}
            <strong style={{ alignSelf: 'flex-end', marginLeft: '0.2rem' }}>Cart</strong>
          </div>
        </div>
      </header>

      {/* Sub-Navbar */}
      <nav className="amz-subnav">
        <div className="amz-subnav-links">
          <a href="#" onClick={() => setView('HOME')}><Menu size={16} /> <strong>All</strong></a>
          <a href="#" onClick={() => setView('HOME')}>Fresh</a>
          <a href="#" onClick={() => setView('HOME')}>Today's Deals</a>
          <a href="#" onClick={() => setView('HOME')}>Electronics</a>
          <a href="#" onClick={() => setView('HOME')}>Prime Video</a>
          <a href="#" onClick={() => setView('HOME')}>Sell</a>
          <a href="#" onClick={() => setView('HOME')}>Gift Cards</a>
          <a href="#" onClick={() => setView('HOME')}>Amazon Pay</a>
          <a href="#" onClick={() => setView('HOME')}>Buy Again</a>
        </div>

        {/* Observability Tools */}
        <div className="amz-obs-tools">
          <button className="obs-btn obs-btn-blue" onClick={triggerTrafficSimulation} disabled={loading}>
            <RefreshCw size={12} className={loading ? "animate-spin" : ""} />
            Generate Load
          </button>
          <button className="obs-btn obs-btn-red" onClick={triggerErrorSimulation}>
            <AlertTriangle size={12} />
            Simulate Error
          </button>
          <a
            href="http://localhost:5080"
            target="_blank"
            rel="noopener noreferrer"
            className="obs-btn obs-btn-green"
          >
            Observability Dashboard <ExternalLink size={12} />
          </a>
        </div>
      </nav>

      {/* VIEW 1: HOME PAGE - PRODUCTS ONLY */}
      {view === 'HOME' && (
        <main className="amz-catalog-section">
          <div className="catalog-header">
            <h2>Featured Products</h2>
            <span style={{ color: 'var(--text-muted)', fontSize: '0.9rem' }}>Showing {products.length} Items</span>
          </div>

          <div className="product-row">
            {products.map(product => (
              <div key={product.id} className="amz-product-card">
                <img src={product.imageUrl} alt={product.name} />
                <h4 className="amz-product-title">{product.name}</h4>
                <div className="amz-rating">Rating: {product.rating} / 5.0</div>
                <div className="amz-price-tag">${product.price.toFixed(2)}</div>
                <span style={{ fontSize: '0.75rem', color: product.stock <= 5 ? '#dc2626' : '#16a34a', margin: '0.4rem 0' }}>
                  {product.stock <= 5 ? `Only ${product.stock} left in stock.` : 'In Stock'}
                </span>
                <button className="amz-add-btn" onClick={() => addToCart(product)}>
                  Add to Cart
                </button>
              </div>
            ))}
          </div>
        </main>
      )}

      {/* VIEW 2: DEDICATED CHECKOUT PAGE */}
      {view === 'CHECKOUT' && (
        <div className="checkout-container">
          <button onClick={() => setView('HOME')} style={{ background: 'none', border: 'none', color: '#007185', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, marginBottom: '1rem' }}>
            <ArrowLeft size={16} /> Return to Shopping
          </button>

          <h1 style={{ fontSize: '1.8rem', fontWeight: 800, marginBottom: '1.5rem', borderBottom: '1px solid #ccc', paddingBottom: '0.5rem' }}>
            AmzStore Checkout
          </h1>

          <div className="checkout-grid">
            <div>
              {/* Step 1: Shipping Address */}
              <div className="checkout-box">
                <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', color: '#0f1111' }}>
                  <Truck size={20} color="#f08804" /> 1. Delivery Address
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                  <div className="form-group">
                    <label>Full Name</label>
                    <input type="text" value={shippingAddress.fullName} onChange={e => setShippingAddress({...shippingAddress, fullName: e.target.value})} />
                  </div>
                  <div className="form-group">
                    <label>Street Address</label>
                    <input type="text" value={shippingAddress.street} onChange={e => setShippingAddress({...shippingAddress, street: e.target.value})} />
                  </div>
                  <div className="form-group">
                    <label>City</label>
                    <input type="text" value={shippingAddress.city} onChange={e => setShippingAddress({...shippingAddress, city: e.target.value})} />
                  </div>
                  <div className="form-group">
                    <label>Pin Code</label>
                    <input type="text" value={shippingAddress.zip} onChange={e => setShippingAddress({...shippingAddress, zip: e.target.value})} />
                  </div>
                </div>
              </div>

              {/* Step 2: Payment Method & Error Simulation */}
              <div className="checkout-box">
                <h3 style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '1rem', color: '#0f1111' }}>
                  <CreditCard size={20} color="#f08804" /> 2. Payment Method & Telemetry Controls
                </h3>

                <div className="form-group">
                  <label>Card Number</label>
                  <input type="text" value="4532 •••• •••• 8892" readOnly />
                </div>

                <div style={{ background: '#fef2f2', border: '1px solid #fca5a5', padding: '1rem', borderRadius: '6px', marginTop: '1rem' }}>
                  <label style={{ display: 'block', fontSize: '0.85rem', color: '#991b1b', fontWeight: 700, marginBottom: '0.4rem' }}>
                    Simulate Service Failure (OpenObserve Trace & Log Analysis):
                  </label>
                  <select
                    value={simulationMode}
                    onChange={e => setSimulationMode(e.target.value)}
                    style={{ width: '100%', padding: '0.6rem', borderRadius: '4px', border: '1px solid #dc2626', fontSize: '0.9rem', background: '#fff' }}
                  >
                    <option value="NONE">None (Normal Checkout)</option>
                    <option value="INVENTORY">InventoryService Failure (Out of Stock / DB Lock Timeout)</option>
                    <option value="PAYMENT">PaymentGateway Failure (Card Authorization Code 402)</option>
                    <option value="SHIPPING">FulfillmentService Failure (Carrier API Timeout 503)</option>
                  </select>
                </div>
              </div>
            </div>

            {/* Step 3: Order Summary Sidebar */}
            <div>
              <div className="checkout-box" style={{ sticky: 'top', top: '100px' }}>
                <h3 style={{ marginBottom: '1rem' }}>Order Summary</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem', marginBottom: '1rem' }}>
                  {cart.map(item => (
                    <div key={item.productId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.85rem' }}>
                      <span>{item.productName} (x{item.quantity})</span>
                      <strong>${(item.unitPrice * item.quantity).toFixed(2)}</strong>
                    </div>
                  ))}
                </div>

                <div style={{ borderTop: '1px solid #ddd', paddingTop: '0.75rem', marginBottom: '1.2rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '1.2rem', fontWeight: 800, color: '#b12704' }}>
                    <span>Order Total:</span>
                    <span>${cartTotal.toFixed(2)}</span>
                  </div>
                </div>

                <button
                  className="amz-add-btn"
                  style={{ width: '100%', padding: '0.8rem', fontSize: '1rem' }}
                  onClick={handleCheckoutSubmit}
                  disabled={loading}
                >
                  {loading ? 'Processing...' : `Place Your Order and Pay ($${cartTotal.toFixed(2)})`}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* VIEW 3: AUTHENTIC AMAZON ORDER SUMMARY PAGE */}
      {view === 'SUMMARY' && lastOrder && (
        <div style={{ maxWidth: '900px', margin: '2rem auto', padding: '0 1rem' }}>
          {/* Top Green Notification Card */}
          <div style={{ background: '#fff', border: '1px solid #d5d9d9', borderRadius: '8px', padding: '1.5rem', marginBottom: '1.5rem', display: 'flex', gap: '1rem', alignItems: 'flex-start' }}>
            <div style={{ background: '#007600', color: '#fff', padding: '0.3rem', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Check size={22} />
            </div>
            <div>
              <h1 style={{ fontSize: '1.4rem', fontWeight: 700, color: '#007600', marginBottom: '0.2rem' }}>
                Order placed, thank you
              </h1>
              <p style={{ fontSize: '0.9rem', color: '#565959' }}>
                Confirmation will be sent to <strong>{lastOrder.customerEmail}</strong> | Order # <strong>{lastOrder.orderId}</strong>
              </p>
            </div>
          </div>

          {/* Receipt Details Box */}
          <div style={{ background: '#fff', border: '1px solid #d5d9d9', borderRadius: '8px', padding: '1.5rem', marginBottom: '1.5rem' }}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem', fontSize: '0.9rem', marginBottom: '1.5rem' }}>
              <div>
                <strong style={{ display: 'block', color: '#0f1111', marginBottom: '0.4rem' }}>Shipping Address</strong>
                <span style={{ color: '#565959', lineHeight: 1.5 }}>
                  {shippingAddress.fullName}<br />
                  {shippingAddress.street}<br />
                  {shippingAddress.city}, {shippingAddress.zip}
                </span>
              </div>

              <div>
                <strong style={{ display: 'block', color: '#0f1111', marginBottom: '0.4rem' }}>Guaranteed Delivery:</strong>
                <span style={{ color: '#007600', fontWeight: 700 }}>2 Business Days (Express Shipping)</span>
              </div>
            </div>

            <div style={{ borderTop: '1px solid #e7e7e7', paddingTop: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '1.1rem', fontWeight: 700, color: '#0f1111' }}>Total Paid:</span>
              <span style={{ fontSize: '1.4rem', fontWeight: 800, color: '#b12704' }}>${lastOrder.totalAmount?.toFixed(2)}</span>
            </div>
          </div>

          {/* Integrated Amazon-Style Telemetry Box */}
          <div style={{ background: '#f8fafc', border: '1px dashed #0284c7', borderRadius: '8px', padding: '1.2rem', marginBottom: '1.5rem' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
              <span style={{ fontSize: '0.85rem', color: '#0369a1', fontWeight: 700 }}>OpenObserve Distributed Trace ID:</span>
              <button
                onClick={() => copyTraceId(lastOrder.traceId)}
                style={{ background: '#e0f2fe', color: '#0369a1', border: '1px solid #7dd3fc', padding: '0.3rem 0.7rem', borderRadius: '4px', cursor: 'pointer', fontSize: '0.75rem', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '0.3rem' }}
              >
                <Copy size={12} /> {copied ? 'Copied!' : 'Copy Trace ID'}
              </button>
            </div>
            <div style={{ background: '#fff', border: '1px solid #cbd5e1', color: '#0f172a', fontFamily: 'monospace', fontSize: '0.85rem', padding: '0.6rem 0.8rem', borderRadius: '4px', wordBreak: 'break-all' }}>
              {lastOrder.traceId}
            </div>
          </div>

          <div style={{ textAlign: 'center' }}>
            <button className="amz-add-btn" style={{ padding: '0.75rem 2.5rem', fontSize: '0.95rem' }} onClick={() => setView('HOME')}>
              Continue Shopping
            </button>
          </div>
        </div>
      )}

      {/* Cart Drawer */}
      {isCartOpen && (
        <div className="modal-backdrop" onClick={() => setIsCartOpen(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem', borderBottom: '1px solid #ddd', paddingBottom: '0.5rem' }}>
              <h2>Shopping Cart ({totalCartCount} items)</h2>
              <button onClick={() => setIsCartOpen(false)} style={{ background: 'none', border: 'none', fontSize: '1.2rem', cursor: 'pointer' }}>✕</button>
            </div>

            {cart.length === 0 ? (
              <p style={{ textAlign: 'center', padding: '2rem 0', color: '#666' }}>Your cart is empty. Click "Add to Cart" on any product!</p>
            ) : (
              <>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem', marginBottom: '1rem' }}>
                  {cart.map(item => (
                    <div key={item.productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '0.5rem' }}>
                      <div>
                        <strong>{item.productName}</strong>
                        <div style={{ fontSize: '0.85rem', color: '#666' }}>
                          ${item.unitPrice.toFixed(2)} x {item.quantity} = ${(item.unitPrice * item.quantity).toFixed(2)}
                        </div>
                      </div>

                      <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem' }}>
                        <button onClick={() => updateQuantity(item.productId, -1)} style={{ background: '#e2e8f0', border: 'none', width: '22px', height: '22px', borderRadius: '4px', cursor: 'pointer' }}><Minus size={12} /></button>
                        <span style={{ fontWeight: 700, fontSize: '0.85rem' }}>{item.quantity}</span>
                        <button onClick={() => updateQuantity(item.productId, 1)} style={{ background: '#e2e8f0', border: 'none', width: '22px', height: '22px', borderRadius: '4px', cursor: 'pointer' }}><Plus size={12} /></button>
                        <button onClick={() => removeFromCart(item.productId)} style={{ color: '#dc2626', background: 'none', border: 'none', cursor: 'pointer', marginLeft: '0.4rem' }}><Trash2 size={16} /></button>
                      </div>
                    </div>
                  ))}
                </div>

                <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '1.2rem', fontWeight: 700, marginBottom: '1rem' }}>
                  <span>Subtotal ({totalCartCount} items):</span>
                  <span style={{ color: '#b12704' }}>${cartTotal.toFixed(2)}</span>
                </div>

                <button className="amz-add-btn" style={{ width: '100%', padding: '0.75rem', fontSize: '1rem' }} onClick={proceedToCheckoutPage}>
                  Proceed to Checkout Page
                </button>
              </>
            )}
          </div>
        </div>
      )}

      {/* Observability Toast Notification */}
      {lastTrace && (
        <div className="trace-toast">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.4rem' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.4rem', fontWeight: 700, color: lastTrace.type.includes('ERROR') || lastTrace.type.includes('FAILURE') ? '#ef4444' : '#34d399' }}>
              {lastTrace.type.includes('ERROR') || lastTrace.type.includes('FAILURE') ? <AlertTriangle size={16} /> : <CheckCircle2 size={16} />}
              <span>{lastTrace.title}</span>
            </div>
            <button onClick={() => setLastTrace(null)} style={{ background: 'none', border: 'none', color: '#aaa', cursor: 'pointer' }}>✕</button>
          </div>

          <p style={{ fontSize: '0.85rem', color: '#cbd5e1' }}>{lastTrace.message}</p>

          <div style={{ marginTop: '0.4rem' }}>
            <span style={{ fontSize: '0.75rem', color: '#94a3b8' }}>Generated Trace ID:</span>
            <div className="trace-id-box" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{lastTrace.traceId}</span>
              <button
                onClick={() => copyTraceId(lastTrace.traceId)}
                style={{ background: '#0284c7', color: '#fff', border: 'none', padding: '0.2rem 0.5rem', borderRadius: '4px', cursor: 'pointer', fontSize: '0.7rem', display: 'flex', alignItems: 'center', gap: '0.2rem' }}
              >
                <Copy size={12} />
                {copied ? 'Copied!' : 'Copy'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
