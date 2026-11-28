import com.veloster.vehicle.MaintLayout;

/** 정비's places: the tab pills, the big card on the left, cards and "+ item" chips on the right; every count fits and hits itself. */
public final class MaintLayoutTest {
    private static int checks;
    private static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        for(int i=0;i<3;i++){float l=MaintLayout.PILL_L+i*(MaintLayout.PILL_W+8);
            check(MaintLayout.pill(l+5,MaintLayout.PILL_T+5)==i&&MaintLayout.pill(l+MaintLayout.PILL_W+4,MaintLayout.PILL_T+5)==-1,"pill "+i+" hits itself, the gap nothing");}
        check(MaintLayout.pill(400,MaintLayout.PILL_T+5)==-1,"right of the pills is no pill");
        check(MaintLayout.button(MaintLayout.BTN_L+5,MaintLayout.BTN_T+5)&&MaintLayout.hero(MaintLayout.BTN_L+5,MaintLayout.BTN_T+5),"the big card's button is inside the big card");
        check(MaintLayout.BTN_B-MaintLayout.BTN_T>=44&&MaintLayout.BTN_B<=MaintLayout.BOTTOM-20,"the button is tall enough to tap, with room for its caption");
        check(!MaintLayout.hero(MaintLayout.RIGHT_L+5,200)&&MaintLayout.HERO_R+8<=MaintLayout.RIGHT_L,"the big card stops before the right side");
        // every count the three tabs can have: 교환 up to 10 beside the big card, 상태 up to 6 with the evidence block under them
        for(float tail:new float[]{0,MaintLayout.TAIL_H})for(int n=0;n<=10;n++)for(int m=0;n+m<=(tail>0?7:11);m++){
            MaintLayout.Grid g=MaintLayout.grid(n,m,tail);String w=n+" cards, "+m+" chips, tail "+tail;
            check(g.cards.length==n&&g.chips.length==m,w+": a place each");
            float[][] all=new float[n+m][];for(int i=0;i<n;i++)all[i]=g.cards[i];for(int i=0;i<m;i++)all[n+i]=g.chips[i];
            float last=MaintLayout.TOP;
            for(int i=0;i<all.length;i++){float[] r=all[i];
                check(r[0]>=MaintLayout.RIGHT_L&&r[2]<=MaintLayout.RIGHT_R+0.01f&&r[1]>=MaintLayout.TOP&&r[3]<=MaintLayout.BOTTOM+0.01f,w+": place "+i+" inside the right side");
                check(r[3]-r[1]>=40,w+": place "+i+" tall enough to tap ("+(r[3]-r[1])+")");
                check(MaintLayout.hit(all,(r[0]+r[2])/2,(r[1]+r[3])/2)==i,w+": place "+i+" hits itself");
                last=Math.max(last,r[3]);}
            if(tail>0)check(g.tailT>=last&&g.tailT+tail<=MaintLayout.BOTTOM+0.01f,w+": the tail under the rest, inside");
            if(n>0)check(g.cards[0][1]>=g.cardsLabel,w+": the cards' heading above them");
            if(m>0)check(g.chips[0][1]>=g.chipsLabel&&(n==0||g.chipsLabel>g.cards[n-1][3]),w+": the chips' heading under the cards");
        }
        MaintLayout.Grid few=MaintLayout.grid(4,6,0);
        check(few.tall&&few.cards[0][3]-few.cards[0][1]==MaintLayout.CARD_MAX,"few cards: full height, two lines");
        check(few.cards[1][1]==few.cards[0][1]&&few.cards[1][0]>few.cards[0][2],"cards two to a row");
        check(few.chips[2][1]==few.chips[0][1]&&few.chips[3][1]>few.chips[0][3],"chips three to a row");
        check(!MaintLayout.grid(10,0,0).tall,"ten cards: one line each");
        check(MaintLayout.hit(few.cards,MaintLayout.RIGHT_L-4,few.cards[0][1]+5)==-1&&MaintLayout.hit(few.cards,few.cards[0][2]+4,few.cards[0][1]+5)==-1,"left of the cards and the gap between them hit nothing");
        System.out.println("PASS "+checks+" maintenance layout checks");
    }
}
