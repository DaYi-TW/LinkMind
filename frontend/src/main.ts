import 'zone.js';
import { bootstrapApplication } from '@angular/platform-browser';
import { AfterViewInit, Component, ElementRef, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient, provideHttpClient } from '@angular/common/http';
import ForceGraph3D from '3d-force-graph';

type NodeType = 'Disease'|'Drug'|'Lab'|'Document'|'Concept';
interface GNode { id:string; label:string; type:NodeType; x:number; y:number; size:number; description:string; docs:number; links:string[] }
interface Edge { id?:string;a:string;b:string;label:string }
interface GraphResponse { nodes:GNode[];edges:Edge[] }

const NODES:GNode[]=[
 {id:'diabetes',label:'糖尿病',type:'Disease',x:50,y:43,size:34,description:'慢性代謝性疾病，與胰島素分泌或作用異常相關。',docs:28,links:['metformin','hba1c','kidney','insulin']},
 {id:'metformin',label:'Metformin',type:'Drug',x:68,y:28,size:27,description:'第一線口服降血糖藥物，可降低肝臟葡萄糖生成。',docs:12,links:['diabetes','kidney','lactic']},
 {id:'hba1c',label:'HbA1c',type:'Lab',x:31,y:25,size:23,description:'反映近 2–3 個月平均血糖控制狀態的檢驗指標。',docs:17,links:['diabetes','guideline']},
 {id:'kidney',label:'腎功能',type:'Concept',x:76,y:55,size:22,description:'影響 Metformin 用藥評估與劑量調整的重要因素。',docs:9,links:['metformin','lactic']},
 {id:'lactic',label:'乳酸中毒',type:'Disease',x:66,y:74,size:19,description:'少見但嚴重的不良反應，腎功能不全時風險增加。',docs:6,links:['metformin','kidney']},
 {id:'insulin',label:'胰島素阻抗',type:'Concept',x:31,y:66,size:23,description:'第二型糖尿病的重要病理機轉。',docs:21,links:['diabetes','exercise']},
 {id:'guideline',label:'ADA 2025 指引',type:'Document',x:14,y:40,size:19,description:'糖尿病照護標準與臨床決策參考文件。',docs:1,links:['hba1c','diabetes']},
 {id:'exercise',label:'運動介入',type:'Concept',x:43,y:81,size:18,description:'改善胰島素敏感性與代謝指標的非藥物策略。',docs:14,links:['insulin']}
];
const EDGES:Edge[]=[{a:'diabetes',b:'metformin',label:'TREATED_BY'},{a:'diabetes',b:'hba1c',label:'MEASURED_BY'},{a:'metformin',b:'kidney',label:'CAUTION_WITH'},{a:'kidney',b:'lactic',label:'RISK_OF'},{a:'metformin',b:'lactic',label:'MAY_CAUSE'},{a:'diabetes',b:'insulin',label:'RELATED_TO'},{a:'hba1c',b:'guideline',label:'REFERENCED_BY'},{a:'guideline',b:'diabetes',label:'DESCRIBES'},{a:'insulin',b:'exercise',label:'IMPROVED_BY'}];

@Component({selector:'app-root',standalone:true,imports:[CommonModule],templateUrl:'./app.html'})
export class App implements AfterViewInit {
 @ViewChild('graphHost') graphHost?:ElementRef<HTMLDivElement>; private graph:any; private resize?:ResizeObserver;
 private http=inject(HttpClient); private api='http://127.0.0.1:8000/api';
 nodes=NODES; edges=EDGES; selected=signal(NODES[1]); query=signal(''); expanded=signal(new Set(NODES.map(n=>n.id))); apiState=signal<'loading'|'online'|'offline'>('loading'); linkMessage=signal(''); chat=signal([{who:'ai',text:'知識空間已載入。我可以追蹤實體關聯、比對證據，或整理目前圖譜。'}]);
 constructor(){this.loadGraph();effect(()=>{this.query();this.selected();if(this.graph)this.styleGraph()})}
 ngAfterViewInit(){this.initGraph()}
 initGraph(){if(!this.graphHost)return;const el=this.graphHost.nativeElement;this.graph=(ForceGraph3D as any)()(el).backgroundColor('#07100e').showNavInfo(false).nodeLabel((n:any)=>`<div class="graph-tooltip"><b>${n.label}</b><span>${n.type}</span></div>`).nodeVal((n:any)=>Math.max(3,n.size/5)).nodeResolution(20).linkWidth(0.7).linkOpacity(0.32).linkDirectionalParticles(2).linkDirectionalParticleWidth(1.4).linkDirectionalParticleSpeed(0.004).linkColor(()=> '#63a996').linkDirectionalParticleColor(()=> '#83f6c0').onNodeClick((n:any)=>{this.select(n as GNode);const distance=90,ratio=1+distance/Math.hypot(n.x,n.y,n.z);this.graph.cameraPosition({x:n.x*ratio,y:n.y*ratio,z:n.z*ratio},n,900)}).onNodeHover((n:any)=>el.style.cursor=n?'pointer':'grab');this.resize=new ResizeObserver(()=>this.graph.width(el.clientWidth).height(el.clientHeight));this.resize.observe(el);this.renderGraph()}
 renderGraph(){if(!this.graph)return;this.graph.graphData({nodes:this.nodes.map(n=>({...n})),links:this.edges.map(e=>({source:e.a,target:e.b,label:e.label}))});this.styleGraph();setTimeout(()=>this.graph.zoomToFit(700,55),350)}
 styleGraph(){const q=this.query().trim().toLowerCase(),selected=this.selected().id;this.graph.nodeColor((n:any)=>{if(q&&!n.label.toLowerCase().includes(q)&&!n.type.toLowerCase().includes(q))return '#152420';if(n.id===selected)return '#ffffff';return ({Disease:'#ff6374',Drug:'#ffb05f',Lab:'#37d7d0',Document:'#6f9cff',Concept:'#83f6c0'} as any)[n.type]??'#83f6c0'}).nodeOpacity(0.92).nodeRelSize(4.5)}
 loadGraph(){this.apiState.set('loading');this.http.get<GraphResponse>(`${this.api}/graph`).subscribe({next:g=>{this.nodes=g.nodes;this.edges=g.edges;this.expanded.set(new Set(g.nodes.map(n=>n.id)));this.selected.set(g.nodes.find(n=>n.id==='metformin')??g.nodes[0]);this.apiState.set('online');this.renderGraph()},error:()=>this.apiState.set('offline')})}
 addLink(target:string,relation:string){this.linkMessage.set('');this.http.post<Edge>(`${this.api}/graph/links`,{sourceId:this.selected().id,targetId:target,relation}).subscribe({next:e=>{this.edges=[...this.edges,e];this.linkMessage.set('LINK CREATED');this.renderGraph()},error:e=>this.linkMessage.set(e.error?.message??'Unable to create link')})}
 visible=computed(()=>{const q=this.query().trim().toLowerCase();return new Set(this.nodes.filter(n=>!q||n.label.toLowerCase().includes(q)||n.type.toLowerCase().includes(q)).map(n=>n.id))});
 node(id:string){return this.nodes.find(n=>n.id===id)!} select(n:GNode){this.selected.set(n);const s=new Set(this.expanded());n.links.forEach(x=>s.add(x));s.add(n.id);this.expanded.set(s)}
 ask(input:HTMLInputElement|HTMLTextAreaElement){const q=input.value.trim();if(!q)return;this.chat.update(c=>[...c,{who:'me',text:q}]);input.value='';setTimeout(()=>{this.chat.update(c=>[...c,{who:'ai',text:'圖譜顯示 Metformin 與糖尿病直接相關，並透過腎功能連到乳酸中毒風險。已標示 3 個相關節點與 12 篇來源文件。'}]);['metformin','diabetes','kidney'].forEach(id=>{const n=this.node(id);this.select(n)});this.selected.set(this.node('metformin'))},450)}
 line(e:Edge){const a=this.node(e.a),b=this.node(e.b);return {x1:a.x+'%',y1:a.y+'%',x2:b.x+'%',y2:b.y+'%'}}
}
bootstrapApplication(App,{providers:[provideHttpClient()]}).catch(console.error);
