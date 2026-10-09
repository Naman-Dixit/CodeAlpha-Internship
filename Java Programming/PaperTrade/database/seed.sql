USE papertrade;

-- Simulated starting prices (NOT real market data).
INSERT IGNORE INTO stocks (symbol, company_name, current_price, sector) VALUES
 ('AAPL',  'Apple Inc.',        220.45, 'Technology'),
 ('MSFT',  'Microsoft',         410.20, 'Technology'),
 ('GOOGL', 'Alphabet Inc.',     175.60, 'Technology'),
 ('AMZN',  'Amazon',            195.10, 'Consumer'),
 ('NVDA',  'NVIDIA',            180.30, 'Technology'),
 ('TSLA',  'Tesla',             245.75, 'Automotive'),
 ('META',  'Meta Platforms',    520.90, 'Technology'),
 ('NFLX',  'Netflix',           640.35, 'Entertainment');
