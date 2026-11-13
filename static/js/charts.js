// Interactive Canvas Analytics Chart for Krishna Enterprises
const KrishnaCharts = {
  canvas: null,
  ctx: null,
  data: [],
  hoverIndex: -1,

  init(canvasId, trendsData) {
    this.canvas = document.getElementById(canvasId);
    if (!this.canvas) return;
    this.ctx = this.canvas.getContext('2d');
    this.data = trendsData || [];
    this.setupListeners();
    this.render();
  },

  setData(trendsData) {
    this.data = trendsData || [];
    this.render();
  },

  setupListeners() {
    if (!this.canvas) return;

    const handlePointer = (e) => {
      const rect = this.canvas.getBoundingClientRect();
      const x = (e.clientX || (e.touches && e.touches[0].clientX)) - rect.left;
      const count = this.data.length;
      if (count === 0) return;
      const step = rect.width / count;
      const idx = Math.min(count - 1, Math.max(0, Math.floor(x / step)));
      if (this.hoverIndex !== idx) {
        this.hoverIndex = idx;
        this.render();
      }
    };

    this.canvas.addEventListener('mousemove', handlePointer);
    this.canvas.addEventListener('touchmove', handlePointer, { passive: true });
    this.canvas.addEventListener('mouseleave', () => {
      this.hoverIndex = -1;
      this.render();
    });
  },

  render() {
    if (!this.canvas || !this.ctx) return;
    const ctx = this.ctx;
    const width = this.canvas.width = this.canvas.parentElement.clientWidth || 600;
    const height = this.canvas.height = 260;

    ctx.clearRect(0, 0, width, height);

    if (!this.data || this.data.length === 0) {
      ctx.fillStyle = '#64748b';
      ctx.font = '14px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText('No attendance/wage data available for current range', width / 2, height / 2);
      return;
    }

    const padding = { top: 30, right: 25, bottom: 50, left: 55 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const maxWage = Math.max(...this.data.map((d) => d.gross_wage), 5000);
    const count = this.data.length;
    const stepX = chartW / (count - 1 || 1);

    // Draw horizontal grid lines
    ctx.strokeStyle = '#e2e8f0';
    ctx.lineWidth = 1;
    ctx.fillStyle = '#64748b';
    ctx.font = '11px sans-serif';
    ctx.textAlign = 'right';

    for (let i = 0; i <= 4; i++) {
      const yVal = (maxWage / 4) * i;
      const y = padding.top + chartH - (yVal / maxWage) * chartH;
      ctx.beginPath();
      ctx.moveTo(padding.left, y);
      ctx.lineTo(width - padding.right, y);
      ctx.stroke();
      ctx.fillText(`₹${Math.round(yVal)}`, padding.left - 8, y + 4);
    }

    // 1. Draw Consistency Bar Chart in background
    const barW = Math.min(28, stepX * 0.4);
    this.data.forEach((d, i) => {
      const x = padding.left + i * stepX;
      const consistencyH = (d.consistency / 100) * (chartH * 0.65);
      const barY = padding.top + chartH - consistencyH;

      // Stacked visual bars
      ctx.fillStyle = 'rgba(245, 158, 11, 0.18)'; // Amber light
      ctx.fillRect(x - barW / 2, barY, barW, consistencyH);

      // Bar border
      ctx.strokeStyle = 'rgba(245, 158, 11, 0.45)';
      ctx.strokeRect(x - barW / 2, barY, barW, consistencyH);

      // X-axis date labels
      ctx.fillStyle = '#475569';
      ctx.font = '11px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(d.label || d.date, x, height - 16);
    });

    // 2. Draw Gross Wage Curve with smooth gradient area fill
    const points = this.data.map((d, i) => ({
      x: padding.left + i * stepX,
      y: padding.top + chartH - (d.gross_wage / maxWage) * chartH
    }));

    // Gradient fill
    const grad = ctx.createLinearGradient(0, padding.top, 0, padding.top + chartH);
    grad.addColorStop(0, 'rgba(16, 185, 129, 0.35)'); // Green top
    grad.addColorStop(1, 'rgba(16, 185, 129, 0.02)'); // Transparent bottom

    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 0; i < points.length - 1; i++) {
      const xc = (points[i].x + points[i + 1].x) / 2;
      const yc = (points[i].y + points[i + 1].y) / 2;
      ctx.quadraticCurveTo(points[i].x, points[i].y, xc, yc);
    }
    ctx.lineTo(points[points.length - 1].x, points[points.length - 1].y);
    ctx.lineTo(points[points.length - 1].x, padding.top + chartH);
    ctx.lineTo(points[0].x, padding.top + chartH);
    ctx.closePath();
    ctx.fillStyle = grad;
    ctx.fill();

    // Line stroke
    ctx.beginPath();
    ctx.moveTo(points[0].x, points[0].y);
    for (let i = 0; i < points.length - 1; i++) {
      const xc = (points[i].x + points[i + 1].x) / 2;
      const yc = (points[i].y + points[i + 1].y) / 2;
      ctx.quadraticCurveTo(points[i].x, points[i].y, xc, yc);
    }
    ctx.lineTo(points[points.length - 1].x, points[points.length - 1].y);
    ctx.strokeStyle = '#059669';
    ctx.lineWidth = 3;
    ctx.stroke();

    // Data dots
    points.forEach((p, idx) => {
      ctx.beginPath();
      ctx.arc(p.x, p.y, idx === this.hoverIndex ? 7 : 4, 0, Math.PI * 2);
      ctx.fillStyle = idx === this.hoverIndex ? '#d97706' : '#10b981';
      ctx.fill();
      ctx.strokeStyle = '#ffffff';
      ctx.lineWidth = 2;
      ctx.stroke();
    });

    // 3. Hover / Touch Scrub Tooltip
    if (this.hoverIndex >= 0 && this.hoverIndex < this.data.length) {
      const item = this.data[this.hoverIndex];
      const p = points[this.hoverIndex];

      // Vertical guide line
      ctx.beginPath();
      ctx.setLineDash([4, 4]);
      ctx.moveTo(p.x, padding.top);
      ctx.lineTo(p.x, padding.top + chartH);
      ctx.strokeStyle = '#0f172a';
      ctx.lineWidth = 1;
      ctx.stroke();
      ctx.setLineDash([]);

      // Tooltip Card
      const ttW = 160;
      const ttH = 80;
      let ttX = p.x - ttW / 2;
      if (ttX < padding.left) ttX = padding.left + 5;
      if (ttX + ttW > width - padding.right) ttX = width - padding.right - ttW - 5;
      const ttY = Math.max(10, p.y - ttH - 12);

      // Bubble
      ctx.fillStyle = '#0f172a';
      ctx.beginPath();
      ctx.roundRect(ttX, ttY, ttW, ttH, 8);
      ctx.fill();

      // Tooltip Text
      ctx.fillStyle = '#f8fafc';
      ctx.font = 'bold 11px sans-serif';
      ctx.textAlign = 'left';
      ctx.fillText(`${item.label || item.date}`, ttX + 10, ttY + 18);

      ctx.fillStyle = '#34d399'; // Emerald
      ctx.font = '12px sans-serif';
      ctx.fillText(`Gross: ₹${item.gross_wage.toLocaleString()}`, ttX + 10, ttY + 36);

      ctx.fillStyle = '#fbbf24'; // Amber
      ctx.font = '10px sans-serif';
      ctx.fillText(`OT: ${item.overtime_hours} hrs | Consist: ${item.consistency}%`, ttX + 10, ttY + 52);

      ctx.fillStyle = '#cbd5e1';
      ctx.fillText(`P: ${item.present_count} | H: ${item.half_day_count} | A: ${item.absent_count}`, ttX + 10, ttY + 68);
    }
  }
};
