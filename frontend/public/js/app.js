function toggleTheme(){document.body.classList.toggle('dark');localStorage.setItem('sw-theme',document.body.classList.contains('dark')?'dark':'light')}
if(localStorage.getItem('sw-theme')==='dark')document.body.classList.add('dark');
function money(n){return '₹'+Number(n||0).toLocaleString('en-IN',{minimumFractionDigits:2,maximumFractionDigits:2})}
function updateLive(){
  const salary=Math.max(0,Number(document.querySelector('[name=salary]')?.value||0));
  const other=Math.max(0,Number(document.querySelector('[name=otherIncome]')?.value||0));
  const spending=document.getElementById('monthlySpending');
  const categoryNames=['grocery','housing','travel','food','shopping','utilities','education','healthcare','entertainment','other'];
  const categoryTotal=categoryNames.reduce((sum,name)=>{
    const value=Number(document.querySelector(`[name=${name}]`)?.value||0);
    return sum+(Number.isFinite(value)&&value>0?value:0);
  },0);
  const typedOverall=Math.max(0,Number(spending?.value||0));
  const effective=Math.max(typedOverall,categoryTotal);

  // Keep exactly what the user typed in the input. Do not replace it with
  // 35000.00 or the category total while the user is entering the form.
  if(spending) spending.dataset.corrected=categoryTotal>typedOverall?'true':'false';

  const el=document.getElementById('liveBalance');
  if(el)el.textContent=money(salary+other-effective);

  const effectiveEl=document.getElementById('effectiveSpending');
  if(effectiveEl)effectiveEl.textContent=money(effective);

  const hint=document.getElementById('categoryWarning');
  if(hint){
    hint.textContent=categoryTotal>typedOverall
      ? `Category total ${money(categoryTotal)} is higher than your overall estimate ${money(typedOverall)}. The higher category total will be used for planning.`
      : `Category total ${money(categoryTotal)} is within your overall estimate.`;
    hint.className=categoryTotal>typedOverall?'description warning-text':'description';
  }
}
const spendingInput=document.getElementById('monthlySpending');
if(spendingInput)spendingInput.addEventListener('input',()=>{spendingInput.dataset.userValue=spendingInput.value;updateLive()});
document.querySelectorAll('input').forEach(x=>x.addEventListener('input',updateLive));updateLive();
async function saveOCR(){
 const amount=Number(document.getElementById('ocrAmount').value),description=document.getElementById('ocrDescription').value.trim(),category=document.getElementById('ocrCategory').value,date=document.getElementById('ocrDate').value;
 if(!amount||amount<=0||!description){alert('Please verify the extracted amount and description. Date is optional.');return}
 const body=new URLSearchParams({amount,description,category});if(date)body.set('date',date);
 const button=document.querySelector('button[onclick="saveOCR()"]');if(button){button.disabled=true;button.textContent='Saving…'}
 try{const r=await fetch('/expense/ocr',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});const j=await r.json();document.getElementById('ocrStatus').textContent=j.message;if(j.ok)setTimeout(()=>location.reload(),700)}catch(e){document.getElementById('ocrStatus').textContent='Could not save. Please try again.'}finally{if(button){button.disabled=false;button.textContent='Save OCR transaction'}}
}
const image=document.getElementById('ocrImage');
if(image)image.addEventListener('change',async()=>{
 const file=image.files[0];if(!file)return;const status=document.getElementById('ocrStatus');status.textContent='Reading screenshot…';
 try{
  const result=await Tesseract.recognize(file,'eng',{logger:m=>{if(m.status==='recognizing text')status.textContent=`Reading screenshot… ${Math.round((m.progress||0)*100)}%`}});
  const text=(result.data.text||'').replace(/\u00a0/g,' ');status.textContent='OCR complete. Check the highlighted details before saving.';
  const amountPattern=/(?:₹|rs\.?|inr)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)/gi;
  const candidates=[];
  for(const m of text.matchAll(amountPattern)){
   const value=Number(m[1].replace(/,/g,''));if(!(value>0&&value<100000000))continue;
   const context=text.slice(Math.max(0,m.index-65),Math.min(text.length,m.index+35));let score=0;
   if(/amount\s*(paid|payable|debited)?|total\s*(paid|amount)?|paid\s*amount|transaction amount/i.test(context))score+=10;
   if(/debit(ed)?|sent|payment successful|you paid/i.test(context))score+=6;
   if(/available balance|current balance|closing balance|opening balance|reference|ref\s*(no|id)|upi\s*id|account number/i.test(context))score-=12;
   if(/[₹]|\bINR\b|\bRs\.?/i.test(m[0]))score+=2;
   candidates.push({value,score,index:m.index});
  }
  candidates.sort((a,b)=>b.score-a.score || a.index-b.index);
  if(candidates.length&&candidates[0].score>=0)document.getElementById('ocrAmount').value=candidates[0].value.toFixed(2).replace(/\.00$/,'');
  else status.textContent='Could not confidently detect the amount. Enter it manually; other fields may still be extracted.';
  let foundDate='';
  const iso=text.match(/\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b/);
  const local=text.match(/\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b/);
  if(iso){foundDate=`${iso[1]}-${iso[2].padStart(2,'0')}-${iso[3].padStart(2,'0')}`}
  else if(local){let a=Number(local[1]),b=Number(local[2]),y=local[3];if(y.length===2)y='20'+y;let day=a,month=b;if(a<=12&&b>12){month=a;day=b}foundDate=`${y}-${String(month).padStart(2,'0')}-${String(day).padStart(2,'0')}`;}
  if(foundDate){const d=new Date(foundDate+'T00:00:00');if(!Number.isNaN(d.getTime())&&d.getFullYear()===Number(foundDate.slice(0,4)))document.getElementById('ocrDate').value=foundDate;}
  const lines=text.split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
  const merchant=lines.find(x=>/[a-z]{3,}/i.test(x)&&!/upi|transaction|successful|reference|ref no|date|time|paid|debited|amount|balance|₹|rs\.?|inr|account|bank/i.test(x));
  const desc=document.getElementById('ocrDescription');if(merchant)desc.value=merchant.replace(/[^\w &.'-]/g,'').slice(0,80);else if(!desc.value)desc.value='OCR transaction';
 }catch(e){status.textContent='OCR could not read this image. Try a clear, cropped screenshot or enter details manually.'}
});
function startVoiceExpense(){const Speech=window.SpeechRecognition||window.webkitSpeechRecognition;if(!Speech){alert('Voice entry is not supported by this browser. Try Chrome.');return}const r=new Speech();r.lang='en-IN';r.interimResults=false;r.onstart=()=>document.getElementById('voiceText').textContent='Listening…';r.onresult=async e=>{const text=e.results[0][0].transcript;document.getElementById('voiceText').textContent='Heard: '+text;const m=text.match(/(?:₹|rs\.?|rupees)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)/i);if(!m){alert('Could not find an amount. Please say “spent 250 on lunch”.');return}const amount=Number(m[1].replace(/,/g,''));const desc=text.replace(m[0],'').replace(/spent|spend|on|rupees/gi,' ').trim()||'Cash expense';let category='Other';if(/food|lunch|dinner|breakfast|restaurant/i.test(desc))category='Food';else if(/bus|uber|ola|travel|taxi|fuel/i.test(desc))category='Transport';else if(/shop|clothes|amazon/i.test(desc))category='Shopping';const body=new URLSearchParams({amount,description:desc,category});const res=await fetch('/expense/voice',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body});const j=await res.json();document.getElementById('voiceText').textContent=j.message;if(j.ok)setTimeout(()=>location.reload(),500)};r.start()}
function runSimulator(){const salary=Number(document.getElementById('simSalary').value||0),spend=Number(document.getElementById('simSpend').value||0),save=Number(document.getElementById('simSave').value||0);const baseIncome=Number(document.querySelector('.stats .stat strong')?.textContent.replace(/[^0-9.]/g,'')||0);const baseSpend=Number(document.querySelectorAll('.stats .stat strong')[1]?.textContent.replace(/[^0-9.]/g,'')||0);const income=baseIncome*(1+salary/100),expense=baseSpend+spend,balance=income-expense-save;document.getElementById('simResult').innerHTML=`Scenario balance: <strong>${money(balance)}</strong> per month. ${balance>=0?'This keeps you within income.':'This scenario creates a deficit of '+money(-balance)+'.'}`}

function filterTransactions(){
  const q=(document.getElementById('transactionSearch')?.value||'').toLowerCase().trim();
  const cat=document.getElementById('transactionCategory')?.value||'';
  const source=document.getElementById('transactionSource')?.value||'';
  document.querySelectorAll('.transaction-row').forEach(row=>{
    const text=row.dataset.search||'';
    const ok=(!q||text.includes(q))&&(!cat||row.dataset.category===cat)&&(!source||row.dataset.source===source);
    row.style.display=ok?'grid':'none';
  });
}
function downloadTransactionsCsv(){
  const rows=[['Date','Category','Description','Source','Amount']];
  document.querySelectorAll('#reportData [data-row]').forEach(el=>{
    const parts=(el.dataset.row||'').split('|');
    if(parts.length>=5) rows.push(parts);
  });
  const csv=rows.map(r=>r.map(v=>'"'+String(v).replaceAll('"','""')+'"').join(',')).join('\n');
  const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'}); const a=document.createElement('a'); a.href=URL.createObjectURL(blob); a.download='Smart Saving Plan_Transactions.csv'; a.click(); URL.revokeObjectURL(a.href);
}
function downloadMonthlySummaryCsv(){
  const cards=[...document.querySelectorAll('.report-summary .report-number')];
  const rows=[['Smart Saving Plan Monthly Financial Summary'],['Metric','Amount']];
  cards.forEach(c=>{const label=c.querySelector('span')?.textContent?.trim()||'';const value=c.querySelector('strong')?.textContent?.trim()||'';rows.push([label,value])});
  const insights=[...document.querySelectorAll('.insight-row b')].map(x=>x.textContent.trim());
  if(insights.length){rows.push([]);rows.push(['Financial insights']);insights.forEach(x=>rows.push([x]));}
  const csv=rows.map(r=>r.map(v=>'"'+String(v).replaceAll('"','""')+'"').join(',')).join('\n');
  const blob=new Blob([csv],{type:'text/csv;charset=utf-8;'});const a=document.createElement('a');a.href=URL.createObjectURL(blob);a.download='Smart Saving Plan_Monthly_Summary.csv';a.click();URL.revokeObjectURL(a.href);
}
